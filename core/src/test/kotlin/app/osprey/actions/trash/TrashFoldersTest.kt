package app.osprey.actions.trash

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashFoldersTest {

    private val fs = FakeFileSystemRepository()
    private val trash = TrashFolders(fs)
    private val location = TrashLocation(fs.root, "2019")

    private fun child(parent: FileHandle, name: String): FileHandle =
        fs.list(parent).single { it.isDirectory && it.name == name }.handle

    @Test
    fun `ensure creates Trash, the year and the action folder`() {
        val folder = trash.ensure(location, TrashAction.DEDUPE)
        fs.addFile(folder, "marker.jpg")

        val expected = child(child(child(fs.root, "Trash"), "2019"), "Dedupe")
        assertEquals(listOf("marker.jpg"), fs.list(expected).map { it.name })
    }

    @Test
    fun `ensure reuses an existing folder`() {
        val first = trash.ensure(location, TrashAction.DEDUPE)
        fs.addFile(first, "a.jpg")

        val second = trash.ensure(location, TrashAction.DEDUPE)

        assertEquals(listOf("a.jpg"), fs.list(second).map { it.name })
        assertEquals(1, fs.list(child(fs.root, "Trash")).size)
    }

    @Test
    fun `each action gets its own folder`() {
        trash.ensure(location, TrashAction.DEDUPE)
        trash.ensure(location, TrashAction.VISUAL_DUPES)

        val yearFolder = child(child(fs.root, "Trash"), "2019")
        assertEquals(setOf("Dedupe", "VisualDupes"), fs.list(yearFolder).map { it.name }.toSet())
    }

    @Test
    fun `existingNames is empty and creates nothing when there is no trash`() {
        val names = trash.existingNames(location, TrashAction.DEDUPE)

        assertTrue(names.isEmpty())
        assertTrue(fs.list(fs.root).isEmpty())
    }

    @Test
    fun `existingNames lists what the action folder holds`() {
        fs.addFile(trash.ensure(location, TrashAction.DEDUPE), "a.jpg")

        assertEquals(setOf("a.jpg"), trash.existingNames(location, TrashAction.DEDUPE))
        assertTrue(trash.existingNames(location, TrashAction.VISUAL_DUPES).isEmpty())
        assertTrue(trash.existingNames(TrashLocation(fs.root, "2020"), TrashAction.DEDUPE).isEmpty())
    }

    @Test
    fun `existingNames ignores a file that has the name of a folder on the way`() {
        fs.addFile(fs.root, "Trash")

        assertEquals(emptySet<String>(), trash.existingNames(location, TrashAction.DEDUPE))
    }

    @Test
    fun `existingNames ignores a file named like the year or the action folder`() {
        val trashFolder = fs.addFolder(fs.root, "Trash")
        fs.addFile(trashFolder, "2019")
        assertEquals(emptySet<String>(), trash.existingNames(location, TrashAction.DEDUPE))

        val other = TrashLocation(fs.root, "2020")
        val year = fs.addFolder(trashFolder, "2020")
        fs.addFile(year, TrashAction.DEDUPE.folderName)
        assertEquals(emptySet<String>(), trash.existingNames(other, TrashAction.DEDUPE))
    }

    @Test
    fun `freeTrashName keeps the extension when it suffixes a taken name`() {
        assertEquals("a__dup2.jpg", freeTrashName("a.jpg", setOf("a.jpg", "a__dup1.jpg")))
        assertEquals("a.jpg", freeTrashName("a.jpg", setOf("b.jpg")))
    }
}
