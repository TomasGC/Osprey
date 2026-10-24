package app.osprey.actions.trash

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashLocatorTest {

    private val lib = TrashTestLibrary()
    private val locator = TrashLocator(lib.fs, lib.root)

    private fun emptyFolder(year: String, action: TrashAction) =
        TrashFolders(lib.fs).ensure(TrashLocation(lib.root, year), action)

    @Test
    fun `find returns the file a Trash entry names`() {
        val entry = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")

        assertEquals(entry.handle, locator.find(entry)?.handle)
    }

    @Test
    fun `find returns nothing for a name that is not in the folder`() {
        val entry = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")

        assertNull(locator.find(entry.copy(name = "b.jpg")))
    }

    @Test
    fun `find returns nothing when there is no Trash`() {
        val entry = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val elsewhere = TrashLocator(lib.fs, lib.fs.addFolder(lib.root, "Other"))

        assertNull(elsewhere.find(entry))
    }

    @Test
    fun `find returns nothing when the year or the action folder is missing`() {
        val entry = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")

        assertNull(locator.find(entry.copy(year = "2020")))
        assertNull(locator.find(entry.copy(action = TrashAction.VISUAL_DUPES)))
    }

    @Test
    fun `find never returns a subfolder`() {
        val entry = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val folder = TrashFolders(lib.fs).ensure(TrashLocation(lib.root, "2019"), TrashAction.DEDUPE)
        lib.fs.addFolder(folder, "sub")

        assertNull(locator.find(entry.copy(name = "sub")))
    }

    @Test
    fun `find reads each folder once, however many files it is asked for`() {
        val first = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val second = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        var listings = 0
        val counting = object : FileSystemRepository by lib.fs {
            override fun list(folder: FileHandle): List<FileEntry> {
                listings++
                return lib.fs.list(folder)
            }
        }
        val counted = TrashLocator(counting, lib.root)

        counted.find(first)
        val afterFirst = listings
        counted.find(second)

        assertEquals(afterFirst, listings)
    }

    @Test
    fun `prune does nothing when there is no Trash`() {
        assertEquals(0, locator.prune("2019", TrashAction.DEDUPE))
    }

    @Test
    fun `prune does nothing when the year folder is missing`() {
        lib.trash("2020", TrashAction.DEDUPE, "a.jpg")

        assertEquals(0, locator.prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash", "2020", "Dedupe", "a.jpg"))
    }

    @Test
    fun `prune ignores a file that has the name of the year folder`() {
        lib.fs.addFile(lib.fs.createFolder(lib.root, "Trash"), "2019")

        assertEquals(0, locator.prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash", "2019"))
    }

    @Test
    fun `prune removes an empty action folder and then the empty year folder`() {
        emptyFolder("2019", TrashAction.DEDUPE)

        assertEquals(2, locator.prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash"))
        assertTrue(!lib.exists("Trash", "2019"))
    }

    @Test
    fun `prune removes an empty year folder that has no action folder`() {
        lib.fs.addFolder(lib.fs.createFolder(lib.root, "Trash"), "2019")

        assertEquals(1, locator.prune("2019", TrashAction.DEDUPE))
    }

    @Test
    fun `prune keeps an action folder that still holds a file`() {
        lib.trash("2019", TrashAction.DEDUPE, "a.jpg")

        assertEquals(0, locator.prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash", "2019", "Dedupe", "a.jpg"))
    }

    @Test
    fun `prune keeps a year folder that still holds another action`() {
        emptyFolder("2019", TrashAction.DEDUPE)
        lib.trash("2019", TrashAction.VISUAL_DUPES, "b.jpg")

        assertEquals(1, locator.prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash", "2019", "VisualDupes", "b.jpg"))
    }

    @Test
    fun `prune keeps a folder that turns out not to be empty when it is removed`() {
        emptyFolder("2019", TrashAction.DEDUPE)
        val faulty = FaultyFileSystemRepository(lib.fs).also { it.failDeleteEmptyFolder = { DirectoryNotEmptyException("late file") } }

        assertEquals(0, TrashLocator(faulty, lib.root).prune("2019", TrashAction.DEDUPE))
        assertTrue(lib.exists("Trash", "2019", "Dedupe"))
    }
}
