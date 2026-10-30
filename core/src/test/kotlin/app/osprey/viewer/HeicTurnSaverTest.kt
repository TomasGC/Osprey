package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.media.HeicTurn
import app.osprey.media.HeicTurnResult
import app.osprey.media.Quirks
import app.osprey.media.heic
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PLENTY = Long.MAX_VALUE / 2

/** Counts what the saver writes, and can make the next write land wrong bytes. */
private class HeicRecording(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    var overwrites = mutableListOf<Pair<Long, Int>>()
    var replaces = 0
    var corruptNextWrite = false
    var vanished = false

    override fun readBytes(entry: FileHandle): ByteArray =
        if (vanished) throw IOException("no such file") else inner.readBytes(entry)

    override fun overwriteAt(entry: FileHandle, offset: Long, bytes: ByteArray) {
        overwrites += offset to bytes.size
        val landed = if (corruptNextWrite) ByteArray(bytes.size) { WRONG_BYTE } else bytes
        corruptNextWrite = false
        inner.overwriteAt(entry, offset, landed)
    }

    override fun replaceContent(entry: FileHandle, bytes: ByteArray) {
        replaces++
        inner.replaceContent(entry, bytes)
    }

    private companion object {
        const val WRONG_BYTE: Byte = 0x07
    }
}

class HeicTurnSaverTest {

    private val files = FakeFileSystemRepository()
    private val faulty = FaultyFileSystemRepository(files)
    private val recording = HeicRecording(faulty)
    private var free = PLENTY
    private val saver = TurnSaver(recording) { free }
    private val modified = Instant.parse("2020-01-02T03:04:05Z")
    private val created = Instant.parse("2019-01-02T03:04:05Z")

    private fun photo(bytes: ByteArray, name: String = "a.heic"): FileHandle =
        files.addFile(files.root, name, bytes).also { files.writeTimestamps(it, FileTimestamps(created, modified)) }

    private fun expected(original: ByteArray, quarterTurns: Int): ByteArray =
        when (val turned = HeicTurn.turn(original, quarterTurns)) {
            is HeicTurnResult.Patched -> original.copyOf().also { out -> turned.edits.forEach { it.bytes.copyInto(out, it.offset) } }
            is HeicTurnResult.Rebuilt -> turned.bytes
            else -> throw AssertionError("not a turn: $turned")
        }

    @Test
    fun `isHeic accepts heic and heif in any case and nothing else`() {
        for (name in listOf("a.heic", "A.HEIC", "b.Heif", "c.heif")) assertTrue(name, TurnSaver.isHeic(name))
        for (name in listOf("a.jpg", "a.png", "a", "heic", "a.heic.txt")) assertTrue(name, !TurnSaver.isHeic(name))
    }

    @Test
    fun `a heic with a rotation is patched in place, only the bytes the turn changes are written`() {
        val built = heic(primaryIrot = 3, exifOrientation = 6)
        val entry = photo(built.bytes)

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", 1))

        assertArrayEquals(expected(built.bytes, 1), files.readBytes(entry))
        assertEquals(0, recording.replaces)
        assertEquals(listOf(built.irotAt!!.toLong() to 1, built.exifValueAt!!.toLong() to 2), recording.overwrites)
    }

    @Test
    fun `a heic with no rotation property is replaced as a whole`() {
        val built = heic(primaryIrot = null, exifOrientation = 1)
        val entry = photo(built.bytes)

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", 3))

        assertArrayEquals(expected(built.bytes, 3), files.readBytes(entry))
        assertEquals(1, recording.replaces)
        assertTrue(recording.overwrites.isEmpty())
    }

    @Test
    fun `heif files are saved too`() {
        val built = heic()
        val entry = photo(built.bytes, "a.heif")

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heif", 2))

        assertArrayEquals(expected(built.bytes, 2), files.readBytes(entry))
    }

    @Test
    fun `whole turns write nothing`() {
        val built = heic()
        val entry = photo(built.bytes)

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", 4))
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", -4))

        assertTrue(recording.overwrites.isEmpty() && recording.replaces == 0)
        assertArrayEquals(built.bytes, files.readBytes(entry))
    }

    @Test
    fun `an Exif with no orientation entry is saved and the note comes back`() {
        val built = heic(exifOrientation = null)
        val entry = photo(built.bytes)

        assertEquals(TurnSaveResult.SavedWithNote(HeicTurn.EXIF_NOTE), saver.save(entry, "a.heic", 1))

        assertArrayEquals(expected(built.bytes, 1), files.readBytes(entry))
    }

    @Test
    fun `a rebuilt heic whose Exif has no orientation entry also carries the note`() {
        val entry = photo(heic(primaryIrot = null, exifOrientation = null).bytes)

        assertEquals(TurnSaveResult.SavedWithNote(HeicTurn.EXIF_NOTE), saver.save(entry, "a.heic", 1))
    }

    @Test
    fun `a refusal says why and leaves the file alone`() {
        val cases = mapOf(
            "a mirrored photo" to (heic(mirror = true).bytes to HeicTurn.MIRRORED),
            "a layout that cannot be rebuilt" to (heic(primaryIrot = null, metaFirst = true).bytes to HeicTurn.UNSAFE_LAYOUT),
            "a jpeg named heic" to (byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte(), 0, 0, 0, 0, 0) to HeicTurn.NOT_A_HEIC),
            "damage" to (heic(quirks = Quirks(emptyIrot = true)).bytes to HeicTurn.DAMAGED),
        )
        for ((name, pair) in cases) {
            val entry = photo(pair.first)

            assertEquals(name, TurnSaveResult.Refused(pair.second), saver.save(entry, "a.heic", 1))

            assertArrayEquals(name, pair.first, files.readBytes(entry))
        }
        assertTrue(recording.overwrites.isEmpty() && recording.replaces == 0)
    }

    @Test
    fun `a photo that needs more memory than is free is refused and left alone`() {
        val built = heic()
        val entry = photo(built.bytes)
        free = built.bytes.size * 5L - 1

        val result = saver.save(entry, "a.heic", 1)

        assertEquals(TurnSaveResult.Refused(HeicTurnSaver.TOO_BIG), result)
        assertArrayEquals(built.bytes, files.readBytes(entry))
        free = built.bytes.size * 5L
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", 1))
    }

    @Test
    fun `a write that fails puts every byte back and says why`() {
        val built = heic()
        val entry = photo(built.bytes)
        var writes = 0
        faulty.failOverwrite = { if (++writes == 2) IOException("provider is read-only") else null }

        assertEquals(TurnSaveResult.Failed("provider is read-only"), saver.save(entry, "a.heic", 1))

        assertArrayEquals(built.bytes, files.readBytes(entry))
    }

    @Test
    fun `a write that lands the wrong bytes is put back`() {
        val built = heic()
        val entry = photo(built.bytes)
        recording.corruptNextWrite = true

        assertTrue(saver.save(entry, "a.heic", 1) is TurnSaveResult.Failed)

        assertArrayEquals(built.bytes, files.readBytes(entry))
        assertEquals(modified, files.readTimestamps(entry).modified)
    }

    @Test
    fun `a replacement that fails is reported`() {
        val entry = photo(heic(primaryIrot = null).bytes)
        faulty.failReplace = { IOException("disk full") }

        assertEquals(TurnSaveResult.Failed("disk full"), saver.save(entry, "a.heic", 1))
    }

    @Test
    fun `the modified and created times are kept after either kind of save`() {
        for (primaryIrot in listOf(3, null)) {
            val entry = photo(heic(primaryIrot = primaryIrot).bytes)

            saver.save(entry, "a.heic", 1)

            assertEquals(FileTimestamps(created, modified), files.readTimestamps(entry))
        }
    }

    @Test
    fun `a time that cannot be restored does not undo the save`() {
        val entry = photo(heic().bytes)
        faulty.failWriteTimestamps = { IOException("no") }

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.heic", 1))
    }

    @Test
    fun `a file that vanished is a failure not a crash`() {
        val entry = photo(heic().bytes)
        recording.vanished = true

        assertEquals(TurnSaveResult.Failed("no such file"), saver.save(entry, "a.heic", 1))
    }
}
