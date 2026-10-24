package app.osprey.actions.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashScannerTest {

    private val lib = TrashTestLibrary()

    @Test
    fun `a library without a Trash has nothing`() {
        lib.library("2019", "a.jpg")

        assertTrue(TrashScanner(lib.fs).scan(lib.root).entries.isEmpty())
    }

    @Test
    fun `lists files by year and action with their size`() {
        lib.fs.addFile(TrashFolders(lib.fs).ensure(TrashLocation(lib.root, "2019"), TrashAction.DEDUPE), "a.jpg", ByteArray(5))
        lib.trash("2019", TrashAction.VISUAL_DUPES, "b.jpg")
        lib.trash("2020", TrashAction.DEDUPE, "c.jpg")

        val contents = TrashScanner(lib.fs).scan(lib.root)

        assertEquals(listOf("2019", "2020"), contents.years)
        assertEquals(listOf(TrashAction.DEDUPE, TrashAction.VISUAL_DUPES), contents.actions("2019"))
        assertEquals(listOf(TrashAction.DEDUPE), contents.actions("2020"))
        val a = contents.entries("2019", TrashAction.DEDUPE).single()
        assertEquals("a.jpg", a.name)
        assertEquals(5L, a.sizeBytes)
        assertEquals("Trash/2019/Dedupe/a.jpg", a.label)
    }

    @Test
    fun `ignores what is not year slash known action slash file`() {
        lib.trash("2019", TrashAction.DEDUPE, "keep.jpg")
        val trash = lib.fs.createFolder(lib.root, "Trash")
        lib.fs.addFile(trash, "loose.jpg")
        lib.fs.addFile(lib.fs.addFolder(trash, "notes"), "n.jpg")
        val year = lib.fs.createFolder(trash, "2019")
        lib.fs.addFile(lib.fs.addFolder(year, "Mystery"), "m.jpg")
        lib.fs.addFolder(lib.fs.createFolder(year, "Dedupe"), "sub")

        val names = TrashScanner(lib.fs).scan(lib.root).entries.map { it.name }

        assertEquals(listOf("keep.jpg"), names)
    }

    @Test
    fun `does not read the year folders of the library`() {
        lib.library("2019", "a.jpg")
        lib.fs.addFolder(lib.fs.createFolder(lib.root, "2019"), "Dedupe")

        assertTrue(TrashScanner(lib.fs).scan(lib.root).entries.isEmpty())
    }

    @Test
    fun `a file called Trash is not a Trash folder`() {
        lib.fs.addFile(lib.root, "Trash")

        assertTrue(TrashScanner(lib.fs).scan(lib.root).entries.isEmpty())
    }

    @Test
    fun `a folder with no name is not a year`() {
        val trash = lib.fs.createFolder(lib.root, "Trash")
        lib.fs.addFile(lib.fs.addFolder(lib.fs.addFolder(trash, ""), "Dedupe"), "a.jpg")

        assertTrue(TrashScanner(lib.fs).scan(lib.root).entries.isEmpty())
    }

    @Test
    fun `a folder whose name only starts with digits is not a year`() {
        val trash = lib.fs.createFolder(lib.root, "Trash")
        lib.fs.addFile(lib.fs.addFolder(lib.fs.addFolder(trash, "2019x"), "Dedupe"), "a.jpg")

        assertTrue(TrashScanner(lib.fs).scan(lib.root).entries.isEmpty())
    }
}
