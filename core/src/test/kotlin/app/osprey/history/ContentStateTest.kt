package app.osprey.history

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SHA256_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
private val T0: Instant = Instant.parse("2026-11-04T10:00:00Z")

class ContentStateTest {

    private val fs = FakeFileSystemRepository()
    private val photo = fs.addFile(fs.root, "a.jpg", "abc".toByteArray())

    @Test
    fun `a file's state has its name, size, dates and the hash of its content`() {
        fs.writeTimestamps(photo, FileTimestamps(created = T0, modified = T0.plusSeconds(5)))

        val state = fs.contentStateOf(photo, "a.jpg")

        assertEquals(FileState(3, T0, T0.plusSeconds(5), SHA256_OF_ABC, "a.jpg"), state)
    }

    @Test
    fun `dates the file system cannot give are left out and the state is still given`() {
        val state = fs.contentStateOf(photo, "a.jpg")

        assertEquals(FileState(3, hash = SHA256_OF_ABC, name = "a.jpg"), state)
    }

    @Test
    fun `a file that cannot be read gives no state`() {
        val locked = object : FileSystemRepository by fs {
            override fun contentHash(entry: FileHandle): String = throw IOException("locked")
        }

        assertNull(locked.contentStateOf(photo, "a.jpg"))
    }

    @Test
    fun `a file whose size cannot be read gives no state`() {
        val gone = object : FileSystemRepository by fs {
            override fun size(entry: FileHandle): Long = throw IOException("gone")
        }

        assertNull(gone.contentStateOf(photo, "a.jpg"))
    }
}
