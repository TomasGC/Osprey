package app.osprey.filesystem

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFileSystemRepositoryTest {

    @Test
    fun `list on an empty folder returns an empty list`() {
        val repo = FakeFileSystemRepository()

        assertEquals(emptyList<FileEntry>(), repo.list(repo.root))
    }

    @Test
    fun `list on a folder that does not exist in this repository returns an empty list`() {
        val repoA = FakeFileSystemRepository()
        val repoB = FakeFileSystemRepository()
        val folderFromA = repoA.addFolder(repoA.root, "2024")

        assertEquals(emptyList<FileEntry>(), repoB.list(folderFromA))
    }

    @Test
    fun `list returns every file and folder added directly under the given folder`() {
        val repo = FakeFileSystemRepository()
        repo.addFile(repo.root, "photo.jpg", byteArrayOf(1, 2, 3))
        repo.addFolder(repo.root, "TODELETE")

        val entries = repo.list(repo.root)

        assertEquals(2, entries.size)
        val file = entries.single { it.name == "photo.jpg" }
        assertEquals(false, file.isDirectory)
        assertEquals(3L, file.sizeBytes)
        val folder = entries.single { it.name == "TODELETE" }
        assertTrue(folder.isDirectory)
    }

    @Test
    fun `renaming a fake entry actually changes what list returns next`() {
        val repo = FakeFileSystemRepository()
        val entry = repo.addFile(repo.root, "photo (2).jpg")

        repo.rename(entry, "photo.jpg")

        val names = repo.list(repo.root).map { it.name }
        assertEquals(listOf("photo.jpg"), names)
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `rename throws instead of overwriting an existing name in the same folder`() {
        val repo = FakeFileSystemRepository()
        repo.addFile(repo.root, "photo.jpg")
        val target = repo.addFile(repo.root, "photo (2).jpg")

        repo.rename(target, "photo.jpg")
    }

    @Test
    fun `move relocates an entry out of its old folder and into the destination`() {
        val repo = FakeFileSystemRepository()
        val subfolder = repo.addFolder(repo.root, "2024")
        val entry = repo.addFile(subfolder, "photo.jpg")

        repo.move(entry, repo.root)

        assertEquals(emptyList<FileEntry>(), repo.list(subfolder))
        assertEquals(listOf("2024", "photo.jpg"), repo.list(repo.root).map { it.name }.sorted())
    }

    @Test
    fun `move can rename at the same time`() {
        val repo = FakeFileSystemRepository()
        val todelete = repo.addFolder(repo.root, "TODELETE")
        val extra = repo.addFile(repo.root, "photo.jpg")

        repo.move(extra, todelete, newName = "photo__dup1.jpg")

        assertEquals(listOf("photo__dup1.jpg"), repo.list(todelete).map { it.name })
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `move throws instead of overwriting an existing name in the destination folder`() {
        val repo = FakeFileSystemRepository()
        val todelete = repo.addFolder(repo.root, "TODELETE")
        repo.addFile(todelete, "photo.jpg")
        val extra = repo.addFile(repo.root, "photo.jpg")

        repo.move(extra, todelete)
    }

    @Test
    fun `createFolder is idempotent when the folder already exists`() {
        val repo = FakeFileSystemRepository()
        repo.createFolder(repo.root, "TODELETE")

        repo.createFolder(repo.root, "TODELETE")

        assertEquals(listOf("TODELETE"), repo.list(repo.root).map { it.name })
    }

    @Test
    fun `deleteEmptyFolder removes a folder with no children`() {
        val repo = FakeFileSystemRepository()
        val folder = repo.addFolder(repo.root, "06")

        repo.deleteEmptyFolder(folder)

        assertEquals(emptyList<FileEntry>(), repo.list(repo.root))
    }

    @Test(expected = DirectoryNotEmptyException::class)
    fun `deleteEmptyFolder throws when the folder still has children`() {
        val repo = FakeFileSystemRepository()
        val folder = repo.addFolder(repo.root, "06")
        repo.addFile(folder, "a.jpg")

        repo.deleteEmptyFolder(folder)
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `createFolder throws when the name already exists as a file`() {
        val repo = FakeFileSystemRepository()
        repo.addFile(repo.root, "TODELETE")

        repo.createFolder(repo.root, "TODELETE")
    }

    @Test
    fun `readBytes returns exactly what the file was seeded with`() {
        val repo = FakeFileSystemRepository()
        val entry = repo.addFile(repo.root, "photo.jpg", byteArrayOf(9, 8, 7))

        assertTrue(byteArrayOf(9, 8, 7).contentEquals(repo.readBytes(entry)))
    }

    @Test
    fun `writeTimestamps only overwrites the fields given, leaving the others untouched`() {
        val repo = FakeFileSystemRepository()
        val entry = repo.addFile(repo.root, "photo.jpg")
        val created = Instant.parse("2020-01-01T00:00:00Z")
        val firstModified = Instant.parse("2020-01-02T00:00:00Z")
        repo.writeTimestamps(entry, FileTimestamps(created = created, modified = firstModified))

        val secondModified = Instant.parse("2020-01-03T00:00:00Z")
        repo.writeTimestamps(entry, FileTimestamps(modified = secondModified))

        val timestamps = repo.readTimestamps(entry)
        assertEquals(created, timestamps.created)
        assertEquals(secondModified, timestamps.modified)
    }
}
