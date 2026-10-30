package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.media.ExifOrientation
import app.osprey.media.JpegOrientation
import app.osprey.media.PngTurn
import app.osprey.media.RGB
import app.osprey.media.Samples
import app.osprey.media.TiffOrientation
import app.osprey.media.chunk
import app.osprey.media.chunksOf
import app.osprey.media.decode
import app.osprey.media.exifChunk
import app.osprey.media.ihdrData
import app.osprey.media.jpegWith
import app.osprey.media.pngOf
import app.osprey.media.randomSamples
import app.osprey.media.rawPng
import app.osprey.media.segment
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counts what the saver reads and writes, and can make the next write land the wrong byte. */
private class Recording(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    var writes = 0
    var replaces = 0
    var bytesWritten = 0
    var largestRead = 0
    var corruptNextWrite = false

    override fun readRange(entry: FileHandle, offset: Long, length: Int): ByteArray {
        largestRead = maxOf(largestRead, length)
        return inner.readRange(entry, offset, length)
    }

    override fun overwriteAt(entry: FileHandle, offset: Long, bytes: ByteArray) {
        writes++
        bytesWritten += bytes.size
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

class TurnSaverTest {

    private val files = FakeFileSystemRepository()
    private val faulty = FaultyFileSystemRepository(files)
    private val recording = Recording(faulty)
    private val saver = TurnSaver(recording)
    private val modified = Instant.parse("2020-01-02T03:04:05Z")
    private val created = Instant.parse("2019-01-02T03:04:05Z")

    private fun photo(jpeg: ByteArray = jpegWith(orientation = 1), name: String = "a.jpg"): FileHandle =
        files.addFile(files.root, name, jpeg).also { files.writeTimestamps(it, FileTimestamps(created, modified)) }

    @Test
    fun `isJpeg accepts jpg and jpeg in any case and nothing else`() {
        for (name in listOf("a.jpg", "A.JPEG", "b.Jpg")) assertTrue(name, TurnSaver.isJpeg(name))
        for (name in listOf("a.png", "a.heic", "a", "jpg", "a.jpg.txt")) assertFalse(name, TurnSaver.isJpeg(name))
    }

    @Test
    fun `a turn stores the orientation the exif table gives`() {
        val cases = listOf(Triple(1, 1, ExifOrientation.ROTATE_90), Triple(6, -1, ExifOrientation.NORMAL), Triple(6, 1, ExifOrientation.ROTATE_180))
        for ((stored, turns, expected) in cases) {
            val original = jpegWith(orientation = stored)
            val entry = photo(original, "$stored$turns.jpg")
            assertEquals(TurnSaveResult.Saved, saver.save(entry, "$stored$turns.jpg", turns))
            assertArrayEquals(JpegOrientation.rewrite(original) { expected }, files.readBytes(entry))
        }
    }

    @Test
    fun `exactly one byte of the file changes`() {
        val original = jpegWith(orientation = 1, trailer = ByteArray(300_000) { 5 })
        val entry = photo(original)
        saver.save(entry, "a.jpg", 1)
        val after = files.readBytes(entry)
        assertEquals(original.size, after.size)
        assertEquals(1, original.indices.count { original[it] != after[it] })
        assertEquals(1, recording.writes)
        assertEquals(1, recording.bytesWritten)
    }

    @Test
    fun `the save never reads more than the header limit`() {
        saver.save(photo(jpegWith(orientation = 1, trailer = ByteArray(2 * TurnSaver.HEADER_LIMIT))), "a.jpg", 1)
        assertTrue(recording.largestRead in 1..TurnSaver.HEADER_LIMIT)
    }

    @Test
    fun `whole turns write nothing`() {
        val original = jpegWith(orientation = 6)
        val entry = photo(original)
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.jpg", 4))
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.jpg", -4))
        assertEquals(0, recording.writes)
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `jpg and jpeg in any case are accepted`() {
        for (name in listOf("a.JPG", "b.jpeg", "c.Jpg")) {
            assertEquals(TurnSaveResult.Saved, saver.save(photo(name = name), name, 1))
        }
    }

    @Test
    fun `other formats are refused and left alone`() {
        for (name in listOf("a", "a.gif", "a.bmp")) {
            val original = jpegWith(orientation = 1)
            val entry = photo(original, name)
            assertEquals(TurnSaveResult.Refused(TurnSaver.UNSUPPORTED_FORMAT), saver.save(entry, name, 1))
            assertArrayEquals(original, files.readBytes(entry))
        }
        assertEquals(0, recording.writes)
    }

    @Test
    fun `a jpeg without an orientation tag is refused and left alone`() {
        val original = jpegWith(orientation = null)
        val entry = photo(original)
        assertEquals(TurnSaveResult.Refused(TurnSaver.NO_ORIENTATION_TAG), saver.save(entry, "a.jpg", 1))
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `a tag beyond the header limit is refused`() {
        val padding = List(5) { segment(0xE4, ByteArray(60_000)) }
        val entry = photo(jpegWith(orientation = 1, leading = padding))
        assertEquals(TurnSaveResult.Refused(TurnSaver.NO_ORIENTATION_TAG), saver.save(entry, "a.jpg", 1))
    }

    @Test
    fun `a write that fails leaves the file alone and says why`() {
        val original = jpegWith(orientation = 1)
        val entry = photo(original)
        faulty.failOverwrite = { IOException("provider is read-only") }
        assertEquals(TurnSaveResult.Failed("provider is read-only"), saver.save(entry, "a.jpg", 1))
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `a write that fails once is reported and the file stays as it was`() {
        val original = jpegWith(orientation = 1)
        val entry = photo(original)
        var failed = false
        faulty.failOverwrite = { if (failed) null else IOException("busy").also { failed = true } }
        assertEquals(TurnSaveResult.Failed("busy"), saver.save(entry, "a.jpg", 1))
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `a write that lands the wrong byte is put back`() {
        val original = jpegWith(orientation = 1)
        val entry = photo(original)
        recording.corruptNextWrite = true
        assertTrue(saver.save(entry, "a.jpg", 1) is TurnSaveResult.Failed)
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `the modified and created times are kept after a save`() {
        val entry = photo()
        saver.save(entry, "a.jpg", 1)
        assertEquals(FileTimestamps(created, modified), files.readTimestamps(entry))
    }

    @Test
    fun `the modified time is kept after a put back`() {
        val entry = photo()
        recording.corruptNextWrite = true
        saver.save(entry, "a.jpg", 1)
        assertEquals(modified, files.readTimestamps(entry).modified)
    }

    @Test
    fun `a time that cannot be restored does not undo the save`() {
        val entry = photo()
        faulty.failWriteTimestamps = { IOException("no") }
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.jpg", 1))
    }

    @Test
    fun `a file that vanished is a failure not a crash`() {
        val entry = photo()
        files.delete(entry)
        assertTrue(saver.save(entry, "a.jpg", 1) is TurnSaveResult.Failed)
    }

    private val samples = randomSamples(7, 5, 8, RGB)

    private fun pngPhoto(
        image: Samples = samples,
        before: List<ByteArray> = emptyList(),
        name: String = "a.png",
        interlace: Int = 0,
    ): FileHandle = files.addFile(files.root, name, pngOf(image, before = before, interlace = interlace))
        .also { files.writeTimestamps(it, FileTimestamps(created, modified)) }

    @Test
    fun `isPng accepts png in any case and nothing else`() {
        for (name in listOf("a.png", "A.PNG", "b.Png")) assertTrue(name, TurnSaver.isPng(name))
        for (name in listOf("a.jpg", "a.heic", "a", "png", "a.png.txt")) assertFalse(name, TurnSaver.isPng(name))
    }

    @Test
    fun `a png is saved turned and keeps every pixel`() {
        val entry = pngPhoto()

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.png", 1))

        assertTrue(decode(files.readBytes(entry)).sameAs(samples.oriented(ExifOrientation.ROTATE_90)))
    }

    @Test
    fun `png in any case is accepted`() {
        for (name in listOf("a.PNG", "b.Png")) {
            assertEquals(TurnSaveResult.Saved, saver.save(pngPhoto(name = name), name, 1))
        }
    }

    @Test
    fun `a stored orientation in a png is baked into the pixels and reset to upright`() {
        val entry = pngPhoto(before = listOf(exifChunk(6)))

        saver.save(entry, "a.png", 1)

        val result = files.readBytes(entry)
        assertTrue(decode(result).sameAs(samples.oriented(ExifOrientation.ROTATE_180)))
        val exif = chunksOf(result).first { it.type == "eXIf" }.data
        assertEquals(ExifOrientation.NORMAL, TiffOrientation.read(exif, TiffOrientation.locate(exif)!!))
    }

    @Test
    fun `whole turns of a png write nothing`() {
        val entry = pngPhoto()
        val original = files.readBytes(entry)

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.png", 4))
        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.png", -4))

        assertEquals(0, recording.replaces)
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `a png that cannot be turned is refused with its reason and left alone`() {
        val entry = pngPhoto(interlace = 1)
        val original = files.readBytes(entry)

        assertEquals(TurnSaveResult.Refused(PngTurn.INTERLACED), saver.save(entry, "a.png", 1))

        assertEquals(0, recording.replaces)
        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `a png that cannot be replaced is a failure and the file stays as it was`() {
        val entry = pngPhoto()
        val original = files.readBytes(entry)
        faulty.failReplace = { IOException("disk full") }

        assertEquals(TurnSaveResult.Failed("disk full"), saver.save(entry, "a.png", 1))

        assertArrayEquals(original, files.readBytes(entry))
    }

    @Test
    fun `the modified and created times of a png are kept`() {
        val entry = pngPhoto()

        saver.save(entry, "a.png", 1)

        assertEquals(FileTimestamps(created, modified), files.readTimestamps(entry))
    }

    @Test
    fun `a png time that cannot be restored does not undo the save`() {
        val entry = pngPhoto()
        faulty.failWriteTimestamps = { IOException("no") }

        assertEquals(TurnSaveResult.Saved, saver.save(entry, "a.png", 1))
        assertTrue(decode(files.readBytes(entry)).sameAs(samples.oriented(ExifOrientation.ROTATE_90)))
    }

    @Test
    fun `a png save replaces the file once and writes nothing else`() {
        saver.save(pngPhoto(), "a.png", 1)

        assertEquals(1, recording.replaces)
        assertEquals(0, recording.writes)
    }

    private val rawOfSample = 5 * (7 * 3 + 1)

    @Test
    fun `a png whose copies do not fit the memory is refused before any pixel is read`() {
        val entry = pngPhoto()
        val tight = TurnSaver(recording) { 3L * rawOfSample - 1 }

        assertEquals(TurnSaveResult.Refused(PngTurn.TOO_BIG), tight.save(entry, "a.png", 1))

        assertEquals(0, recording.replaces)
    }

    @Test
    fun `a png whose copies just fit the memory is saved`() {
        val entry = pngPhoto()

        assertEquals(TurnSaveResult.Saved, TurnSaver(recording) { 3L * rawOfSample }.save(entry, "a.png", 1))
    }

    @Test
    fun `no memory at all refuses rather than fails`() {
        val result = TurnSaver(recording) { -5L }.save(pngPhoto(), "a.png", 1)

        assertEquals(TurnSaveResult.Refused(PngTurn.TOO_BIG), result)
    }

    @Test
    fun `a picture over the fixed ceiling is refused however much memory is free`() {
        val huge = rawPng(
            chunk("IHDR", ihdrData(10_000, 10_000, 8, RGB)),
            chunk("IDAT", byteArrayOf(1, 2)),
            chunk("IEND"),
        )
        val entry = files.addFile(files.root, "huge.png", huge)

        val result = TurnSaver(recording) { 4L shl 30 }.save(entry, "huge.png", 1)

        assertEquals(TurnSaveResult.Refused(PngTurn.TOO_BIG), result)
    }
}
