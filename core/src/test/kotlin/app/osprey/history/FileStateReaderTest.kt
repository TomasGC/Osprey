package app.osprey.history

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

private val DAY: Instant = Instant.parse("2019-05-04T12:00:00Z")

class FileStateReaderTest {

    @Test
    fun `a file's state is its size, name and dates`() {
        val fs = FakeFileSystemRepository()
        val photo = fs.addFile(fs.root, "a.jpg", ByteArray(3))
        fs.writeTimestamps(photo, FileTimestamps(created = DAY, modified = DAY.plusSeconds(5)))

        assertEquals(FileState(3, DAY, DAY.plusSeconds(5), name = "a.jpg"), fs.stateOf(photo, "a.jpg", 3))
    }

    @Test
    fun `dates the file system cannot give are left out and the rest is kept`() {
        val inner = FakeFileSystemRepository()
        val photo = inner.addFile(inner.root, "a.jpg", ByteArray(3))
        val fs = object : FileSystemRepository by inner {
            override fun readTimestamps(entry: FileHandle): FileTimestamps = throw IOException("denied")
        }

        assertEquals(FileState(3, name = "a.jpg"), fs.stateOf(photo, "a.jpg", 3))
    }
}
