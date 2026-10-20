package app.osprey.desktop

import app.osprey.actions.visual.VisualDupeMember
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualDupeMoverTest {

    private val fs = DesktopFileSystemRepository()
    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-visual-dupe-mover-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun root() = PathHandle(tempDir)

    private fun write(name: String, content: String = "x"): VisualDupeMember {
        val path = tempDir.resolve(name)
        Files.writeString(path, content)
        return VisualDupeMember(PathHandle(path), name, Files.size(path))
    }

    @Test
    fun `moves every non-kept member into TODELETEDUPS`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")

        val result = moveUncheckedToTodeleteDups(fs, root(), listOf(keep, dup1, dup2), setOf("keep.jpg"))

        assertEquals(setOf("dup1.jpg", "dup2.jpg"), result.moved.toSet())
        assertTrue(result.skipped.isEmpty())
        assertTrue(Files.exists(tempDir.resolve("keep.jpg")))
        assertTrue(Files.exists(tempDir.resolve("TODELETEDUPS/dup1.jpg")))
        assertTrue(Files.exists(tempDir.resolve("TODELETEDUPS/dup2.jpg")))
    }

    @Test
    fun `keeping every member moves nothing and never creates TODELETEDUPS`() {
        val a = write("a.jpg")
        val b = write("b.jpg")

        val result = moveUncheckedToTodeleteDups(fs, root(), listOf(a, b), setOf("a.jpg", "b.jpg"))

        assertTrue(result.moved.isEmpty())
        assertTrue(result.skipped.isEmpty())
        assertTrue(!Files.exists(tempDir.resolve("TODELETEDUPS")))
    }

    @Test
    fun `a name collision in TODELETEDUPS gets a __dup suffix`() {
        val keep = write("keep.jpg")
        val dup = write("photo.jpg")
        Files.createDirectory(tempDir.resolve("TODELETEDUPS"))
        Files.writeString(tempDir.resolve("TODELETEDUPS/photo.jpg"), "already here")

        val result = moveUncheckedToTodeleteDups(fs, root(), listOf(keep, dup), setOf("keep.jpg"))

        assertEquals(listOf("photo.jpg"), result.moved)
        assertTrue(Files.exists(tempDir.resolve("TODELETEDUPS/photo__dup1.jpg")))
        assertEquals("already here", Files.readString(tempDir.resolve("TODELETEDUPS/photo.jpg")))
    }

    @Test
    fun `a member whose file is already gone is skipped, not failed`() {
        val keep = write("keep.jpg")
        val vanished = write("vanished.jpg")
        Files.delete(tempDir.resolve("vanished.jpg"))

        val result = moveUncheckedToTodeleteDups(fs, root(), listOf(keep, vanished), setOf("keep.jpg"))

        assertTrue(result.moved.isEmpty())
        assertEquals(listOf("vanished.jpg"), result.skipped)
    }
}
