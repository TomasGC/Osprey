package app.osprey.actions.visual

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDupeMoveTest {

    private val fs = FakeFileSystemRepository()
    private val root = fs.root

    private fun write(name: String, sizeBytes: Int = 1): VisualDupeMember {
        val handle = fs.addFile(root, name, ByteArray(sizeBytes))
        return VisualDupeMember(handle, name, sizeBytes.toLong())
    }

    private fun exists(parent: FileHandle, name: String): Boolean = fs.list(parent).any { it.name == name }

    @Test
    fun `moves every non-kept member into TODELETEDUPS`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")

        val result = moveUncheckedToTodeleteDups(fs, root, listOf(keep, dup1, dup2), setOf("keep.jpg"))

        assertEquals(setOf("dup1.jpg", "dup2.jpg"), result.moved.toSet())
        assertTrue(result.skipped.isEmpty())
        assertTrue(exists(root, "keep.jpg"))
        val todelete = fs.list(root).single { it.name == "TODELETEDUPS" }.handle
        assertTrue(exists(todelete, "dup1.jpg"))
        assertTrue(exists(todelete, "dup2.jpg"))
    }

    @Test
    fun `keeping every member moves nothing and never creates TODELETEDUPS`() {
        val a = write("a.jpg")
        val b = write("b.jpg")

        val result = moveUncheckedToTodeleteDups(fs, root, listOf(a, b), setOf("a.jpg", "b.jpg"))

        assertTrue(result.moved.isEmpty())
        assertTrue(result.skipped.isEmpty())
        assertTrue(fs.list(root).none { it.name == "TODELETEDUPS" })
    }

    @Test
    fun `a name collision in TODELETEDUPS gets a __dup suffix`() {
        val keep = write("keep.jpg")
        val dup = write("photo.jpg")
        val todelete = fs.addFolder(root, "TODELETEDUPS")
        fs.addFile(todelete, "photo.jpg", "already here".toByteArray())

        val result = moveUncheckedToTodeleteDups(fs, root, listOf(keep, dup), setOf("keep.jpg"))

        assertEquals(listOf("photo.jpg"), result.moved)
        assertTrue(exists(todelete, "photo__dup1.jpg"))
        assertEquals("already here", String(fs.readBytes(fs.list(todelete).single { it.name == "photo.jpg" }.handle)))
    }

    @Test
    fun `a member whose file is already gone is skipped, not failed`() {
        val keep = write("keep.jpg")
        val vanished = VisualDupeMember(fs.addFile(root, "vanished.jpg"), "vanished.jpg", 0)
        fs.rename(vanished.handle, "renamed_away.jpg")

        val result = moveUncheckedToTodeleteDups(fs, root, listOf(keep, vanished), setOf("keep.jpg"))

        assertTrue(result.moved.isEmpty())
        assertEquals(listOf("vanished.jpg"), result.skipped)
    }
}
