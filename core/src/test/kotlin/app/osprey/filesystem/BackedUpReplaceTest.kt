package app.osprey.filesystem

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BACKUP_MARKER = ".osprey-backup-"

/** Hands out a copy whose bytes are not the original's, as a copy cut short would. */
private class CorruptingCopies(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle =
        inner.copyTo(entry, destFolder, newName).also { inner.replaceContent(it, byteArrayOf(0)) }
}

class BackedUpReplaceTest {

    private val files = FakeFileSystemRepository()
    private val faulty = FaultyFileSystemRepository(files)
    private val original = byteArrayOf(1, 2, 3, 4)
    private val replacement = ByteArray(9) { 7 }
    private val entry = files.addFile(files.root, "a.png", original)

    private val goodWrite: (FileHandle, ByteArray) -> Unit = { target, bytes -> files.replaceContent(target, bytes) }

    private fun replace(
        repository: FileSystemRepository = faulty,
        write: (FileHandle, ByteArray) -> Unit = goodWrite,
    ) = BackedUpReplace(repository, repository::readBytes).replace(entry, files.root, "a.png", replacement, write)

    private fun names() = files.list(files.root).map { it.name }

    private fun backup() = files.list(files.root).single { it.name.startsWith("a.png$BACKUP_MARKER") }

    /** A write that leaves the file cut short, then fails: what a full disk does. */
    private fun cutShortThenFail(message: String): (FileHandle, ByteArray) -> Unit = { target, _ ->
        files.replaceContent(target, byteArrayOf(9))
        throw IOException(message)
    }

    /** [first] on the first call, a good write after: the put back must not fail the way the write did. */
    private fun firstThen(first: (FileHandle, ByteArray) -> Unit): (FileHandle, ByteArray) -> Unit {
        var calls = 0
        return { target, bytes -> if (calls++ == 0) first(target, bytes) else goodWrite(target, bytes) }
    }

    @Test
    fun `a good write replaces the content and leaves nothing beside the file`() {
        replace()

        assertArrayEquals(replacement, files.readBytes(entry))
        assertEquals(listOf("a.png"), names())
    }

    @Test
    fun `the write is given the file and the new bytes once`() {
        val calls = mutableListOf<Pair<FileHandle, List<Byte>>>()

        replace(write = { target, bytes -> calls += target to bytes.toList(); goodWrite(target, bytes) })

        assertEquals(listOf(entry to replacement.toList()), calls)
    }

    @Test
    fun `while the write runs a copy of the original sits beside the file`() {
        var seen: List<Byte>? = null

        replace(write = { target, bytes ->
            seen = files.readBytes(backup().handle).toList()
            goodWrite(target, bytes)
        })

        assertEquals(original.toList(), seen)
    }

    @Test
    fun `a write that fails puts the original back removes the copy and says why`() {
        val failure = assertThrows(IOException::class.java) { replace(write = firstThen(cutShortThenFail("disk full"))) }

        assertEquals("disk full", failure.message)
        assertArrayEquals(original, files.readBytes(entry))
        assertEquals(listOf("a.png"), names())
    }

    @Test
    fun `a write that lands other bytes is put back`() {
        val failure = assertThrows(IOException::class.java) {
            replace(write = firstThen { target, _ -> files.replaceContent(target, byteArrayOf(5, 5)) })
        }

        assertEquals(BackedUpReplace.DID_NOT_TAKE, failure.message)
        assertArrayEquals(original, files.readBytes(entry))
        assertEquals(listOf("a.png"), names())
    }

    @Test
    fun `a failed put back keeps the copy and names it`() {
        var calls = 0
        val write: (FileHandle, ByteArray) -> Unit = { target, _ ->
            if (calls++ == 0) cutShortThenFail("disk full")(target, original) else throw IOException("still full")
        }

        val failure = assertThrows(IOException::class.java) { replace(write = write) }

        val kept = backup()
        assertTrue(failure.message.orEmpty().contains(kept.name))
        assertEquals("disk full", failure.cause?.message)
        assertArrayEquals(original, files.readBytes(kept.handle))
    }

    @Test
    fun `a put back that lands other bytes keeps the copy too`() {
        var calls = 0
        val write: (FileHandle, ByteArray) -> Unit = { target, _ ->
            if (calls++ == 0) cutShortThenFail("disk full")(target, original) else files.replaceContent(target, byteArrayOf(8))
        }

        val failure = assertThrows(IOException::class.java) { replace(write = write) }

        assertTrue(failure.message.orEmpty().contains(backup().name))
        assertArrayEquals(original, files.readBytes(backup().handle))
    }

    @Test
    fun `a copy that differs from the original is dropped and nothing is written`() {
        var written = false

        val failure = assertThrows(IOException::class.java) {
            replace(CorruptingCopies(faulty), write = { _, _ -> written = true })
        }

        assertEquals(BackedUpReplace.BACKUP_DIFFERS, failure.message)
        assertEquals(false, written)
        assertArrayEquals(original, files.readBytes(entry))
        assertEquals(listOf("a.png"), names())
    }

    @Test
    fun `a differing copy that cannot be deleted still reports the difference`() {
        faulty.failDelete = { IOException("busy") }

        val failure = assertThrows(IOException::class.java) { replace(CorruptingCopies(faulty)) }

        assertEquals(BackedUpReplace.BACKUP_DIFFERS, failure.message)
    }

    @Test
    fun `a copy that cannot be made stops before any write`() {
        var written = false
        faulty.failCopy = { IOException("no room") }

        val failure = assertThrows(IOException::class.java) { replace(write = { _, _ -> written = true }) }

        assertEquals("no room", failure.message)
        assertEquals(false, written)
        assertArrayEquals(original, files.readBytes(entry))
        assertEquals(listOf("a.png"), names())
    }

    @Test
    fun `a copy that cannot be deleted after a good write does not undo the save`() {
        faulty.failDelete = { IOException("busy") }

        replace()

        assertArrayEquals(replacement, files.readBytes(entry))
    }

    @Test
    fun `a copy that cannot be deleted after a put back still reports the write failure`() {
        faulty.failDelete = { IOException("busy") }

        val failure = assertThrows(IOException::class.java) { replace(write = firstThen(cutShortThenFail("disk full"))) }

        assertEquals("disk full", failure.message)
        assertArrayEquals(original, files.readBytes(entry))
    }
}
