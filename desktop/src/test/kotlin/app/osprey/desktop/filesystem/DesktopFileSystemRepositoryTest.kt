package app.osprey.desktop.filesystem

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileTimestamps
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopFileSystemRepositoryTest {

    private val repo = DesktopFileSystemRepository()
    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-desktop-fs-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun root() = PathHandle(tempDir)

    @Test
    fun `list on an empty folder returns an empty list`() {
        assertTrue(repo.list(root()).isEmpty())
    }

    @Test
    fun `list on a folder that does not exist returns an empty list`() {
        assertTrue(repo.list(PathHandle(tempDir.resolve("missing"))).isEmpty())
    }

    @Test
    fun `list returns files and folders created directly under the given folder`() {
        Files.writeString(tempDir.resolve("photo.jpg"), "abc")
        Files.createDirectory(tempDir.resolve("TODELETE"))

        val entries = repo.list(root())

        assertEquals(2, entries.size)
        val file = entries.single { it.name == "photo.jpg" }
        assertFalse(file.isDirectory)
        assertEquals(3L, file.sizeBytes)
        assertTrue(entries.single { it.name == "TODELETE" }.isDirectory)
    }

    @Test
    fun `rename actually changes what list returns next`() {
        Files.writeString(tempDir.resolve("photo (2).jpg"), "x")
        val entry = repo.list(root()).single()

        repo.rename(entry.handle, "photo.jpg")

        assertEquals(listOf("photo.jpg"), repo.list(root()).map { it.name })
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `rename throws instead of overwriting an existing name`() {
        Files.writeString(tempDir.resolve("photo.jpg"), "a")
        Files.writeString(tempDir.resolve("photo (2).jpg"), "b")
        val target = repo.list(root()).single { it.name == "photo (2).jpg" }

        repo.rename(target.handle, "photo.jpg")
    }

    @Test
    fun `move relocates a file into another real folder`() {
        val sub = Files.createDirectory(tempDir.resolve("2024"))
        Files.writeString(sub.resolve("photo.jpg"), "x")
        val entry = repo.list(PathHandle(sub)).single()

        repo.move(entry.handle, root())

        assertTrue(repo.list(PathHandle(sub)).isEmpty())
        assertTrue(repo.list(root()).any { it.name == "photo.jpg" })
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `move throws instead of overwriting an existing name in the destination`() {
        val todelete = Files.createDirectory(tempDir.resolve("TODELETE"))
        Files.writeString(todelete.resolve("photo.jpg"), "existing")
        Files.writeString(tempDir.resolve("photo.jpg"), "extra")
        val extra = repo.list(root()).single { it.name == "photo.jpg" }

        repo.move(extra.handle, PathHandle(todelete))
    }

    @Test
    fun `createFolder is idempotent when the folder already exists`() {
        repo.createFolder(root(), "TODELETE")

        repo.createFolder(root(), "TODELETE")

        assertEquals(listOf("TODELETE"), repo.list(root()).map { it.name })
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `createFolder throws when the name already exists as a file`() {
        Files.writeString(tempDir.resolve("TODELETE"), "x")

        repo.createFolder(root(), "TODELETE")
    }

    @Test
    fun `deleteEmptyFolder removes a real empty folder`() {
        Files.createDirectory(tempDir.resolve("2024"))

        repo.deleteEmptyFolder(PathHandle(tempDir.resolve("2024")))

        assertTrue(repo.list(root()).isEmpty())
    }

    @Test(expected = DirectoryNotEmptyException::class)
    fun `deleteEmptyFolder throws when the folder still has files`() {
        val sub = Files.createDirectory(tempDir.resolve("2024"))
        Files.writeString(sub.resolve("a.jpg"), "x")

        repo.deleteEmptyFolder(PathHandle(sub))
    }

    @Test
    fun `readBytes returns exactly what was written`() {
        Files.write(tempDir.resolve("photo.jpg"), byteArrayOf(9, 8, 7))
        val entry = repo.list(root()).single()

        assertTrue(byteArrayOf(9, 8, 7).contentEquals(repo.readBytes(entry.handle)))
    }

    @Test
    fun `writeTimestamps then readTimestamps round-trips the modified time`() {
        Files.writeString(tempDir.resolve("photo.jpg"), "x")
        val entry = repo.list(root()).single()
        val modified = Instant.parse("2020-01-02T00:00:00Z").truncatedTo(ChronoUnit.SECONDS)

        repo.writeTimestamps(entry.handle, FileTimestamps(modified = modified))

        assertEquals(modified, repo.readTimestamps(entry.handle).modified)
    }

    @Test
    fun `copyTo copies the content, keeps the source and leaves no temporary file`() {
        val dest = Files.createDirectory(tempDir.resolve("2018"))
        val source = tempDir.resolve("a.jpg").also { Files.writeString(it, "abc") }

        val copy = repo.copyTo(PathHandle(source), PathHandle(dest), "b.jpg")

        assertEquals("abc", Files.readString(dest.resolve("b.jpg")))
        assertEquals(dest.resolve("b.jpg"), (copy as PathHandle).path)
        assertTrue(Files.exists(source))
        assertEquals(listOf("b.jpg"), Files.list(dest).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `copyTo never overwrites an existing file`() {
        val dest = Files.createDirectory(tempDir.resolve("2018"))
        Files.writeString(dest.resolve("a.jpg"), "old")
        val source = tempDir.resolve("a.jpg").also { Files.writeString(it, "new") }

        assertThrows(FileAlreadyExistsException::class.java) { repo.copyTo(PathHandle(source), PathHandle(dest)) }

        assertEquals("old", Files.readString(dest.resolve("a.jpg")))
        assertEquals(listOf("a.jpg"), Files.list(dest).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `delete removes a file`() {
        val file = tempDir.resolve("a.jpg").also { Files.writeString(it, "abc") }

        repo.delete(PathHandle(file))

        assertFalse(Files.exists(file))
    }

    @Test
    fun `contentHash is the SHA-256 of the content`() {
        val file = tempDir.resolve("a.jpg").also { Files.writeString(it, "abc") }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", repo.contentHash(PathHandle(file)))
    }

    @Test
    fun `contentHash reads a file larger than one chunk`() {
        val file = tempDir.resolve("big.mp4")
        Files.write(file, ByteArray(3_000_000) { (it % 251).toByte() })

        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, repo.contentHash(PathHandle(file)))
    }
}
