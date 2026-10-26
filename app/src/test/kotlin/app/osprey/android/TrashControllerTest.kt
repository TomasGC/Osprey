package app.osprey.android

import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrashControllerTest {

    private val fs = FakeFileSystemRepository()
    private val root = fs.root
    private lateinit var controller: TrashController
    private var changed = 0

    @Before
    fun setUp() {
        controller = TrashController(fs, immediateJobQueue()) { changed++ }
    }

    private fun trash(year: String, action: TrashAction, name: String, text: String = name) {
        fs.addFile(TrashFolders(fs).ensure(TrashLocation(root, year), action), name, text.toByteArray())
    }

    private fun library(year: String, name: String, text: String) {
        fs.addFile(fs.createFolder(root, year), name, text.toByteArray())
    }

    private fun entry(year: String, action: TrashAction, name: String) =
        controller.contents.entries(year, action).single { it.name == name }

    private fun namesIn(vararg path: String): Set<String> {
        var folder: FileHandle = root
        for (segment in path) {
            folder = fs.list(folder).firstOrNull { it.isDirectory && it.name == segment }?.handle ?: return emptySet()
        }
        return fs.list(folder).map { it.name }.toSet()
    }

    private fun textOf(year: String, name: String): String {
        val folder = fs.list(root).single { it.isDirectory && it.name == year }.handle
        return String(fs.readBytes(fs.list(folder).single { it.name == name }.handle))
    }

    @Test
    fun `refresh lists the Trash and opens the first year and action`() {
        trash("2020", TrashAction.DEDUPE, "b.jpg")
        trash("2019", TrashAction.VISUAL_DUPES, "a.jpg")

        controller.refresh(root)

        assertEquals(listOf("2019", "2020"), controller.contents.years)
        assertEquals("2019", controller.year)
        assertEquals(TrashAction.VISUAL_DUPES, controller.action)
        assertEquals(listOf("a.jpg"), controller.currentTab.map { it.name })
    }

    @Test
    fun `an empty Trash has no tab`() {
        controller.refresh(root)

        assertNull(controller.year)
        assertNull(controller.action)
        assertTrue(controller.currentTab.isEmpty())
    }

    @Test
    fun `the selection persists when switching tabs`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        trash("2020", TrashAction.DEDUPE, "b.jpg")
        controller.refresh(root)

        controller.toggle(entry("2019", TrashAction.DEDUPE, "a.jpg"))
        controller.selectYear("2020")
        controller.toggle(entry("2020", TrashAction.DEDUPE, "b.jpg"))
        controller.selectYear("2019")

        assertEquals(2, controller.selection.labels.size)
        assertTrue(entry("2019", TrashAction.DEDUPE, "a.jpg") in controller.selection)
    }

    @Test
    fun `select all in a tab selects only that tab`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        trash("2019", TrashAction.DEDUPE, "b.jpg")
        trash("2019", TrashAction.VISUAL_DUPES, "c.jpg")
        controller.refresh(root)

        controller.toggleTab()

        assertEquals(2, controller.selection.labels.size)
        assertFalse(entry("2019", TrashAction.VISUAL_DUPES, "c.jpg") in controller.selection)
        assertTrue(controller.tabFullySelected)
    }

    @Test
    fun `delete asks first and changes nothing until confirmed`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        controller.refresh(root)
        controller.toggleTab()

        controller.requestDelete()

        assertEquals(
            "Permanently delete 1 file (5 B) from Trash/2019/Dedupe? This cannot be undone.",
            controller.confirmation,
        )
        assertEquals(setOf("a.jpg"), namesIn("Trash", "2019", "Dedupe"))
    }

    @Test
    fun `delete with nothing selected asks nothing`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        controller.refresh(root)

        controller.requestDelete()

        assertNull(controller.confirmation)
    }

    @Test
    fun `confirming deletes exactly the selection, refreshes and tells the app`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        trash("2019", TrashAction.DEDUPE, "keep.jpg")
        trash("2020", TrashAction.DEDUPE, "other.jpg")
        controller.refresh(root)
        controller.toggle(entry("2019", TrashAction.DEDUPE, "a.jpg"))
        controller.requestDelete()

        controller.confirmDelete(root)

        assertEquals(setOf("keep.jpg"), namesIn("Trash", "2019", "Dedupe"))
        assertEquals(setOf("other.jpg"), namesIn("Trash", "2020", "Dedupe"))
        assertEquals(2, controller.contents.entries.size)
        assertTrue(controller.selection.isEmpty)
        assertNull(controller.confirmation)
        assertTrue(controller.report.first().contains("DELETED 1"))
        assertEquals(1, changed)
        assertFalse(controller.state.busy)
    }

    @Test
    fun `confirming with nothing selected does nothing`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        controller.refresh(root)

        controller.confirmDelete(root)

        assertEquals(0, changed)
        assertEquals(setOf("a.jpg"), namesIn("Trash", "2019", "Dedupe"))
    }

    @Test
    fun `restore moves the selection back and keeps conflicts for a decision`() {
        trash("2019", TrashAction.DEDUPE, "free.jpg")
        trash("2019", TrashAction.DEDUPE, "taken.jpg", "from trash")
        library("2019", "taken.jpg", "from library")
        controller.refresh(root)
        controller.toggleTab()

        controller.restore(root)

        assertEquals(setOf("taken.jpg", "free.jpg"), namesIn("2019"))
        assertEquals("from library", textOf("2019", "taken.jpg"))
        assertEquals(listOf("taken.jpg"), controller.conflicts.map { it.entry.name })
        assertEquals(1, changed)
    }

    @Test
    fun `restore with nothing selected does nothing`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg")
        controller.refresh(root)

        controller.restore(root)

        assertEquals(0, changed)
    }

    @Test
    fun `resolving a conflict applies the choice and refreshes`() {
        trash("2019", TrashAction.DEDUPE, "taken.jpg", "from trash")
        library("2019", "taken.jpg", "from library")
        controller.refresh(root)
        controller.toggleTab()
        controller.restore(root)

        controller.resolve(root, controller.conflicts.single(), ConflictChoice.BOTH)

        assertEquals("from trash", textOf("2019", "taken__dup1.jpg"))
        assertTrue(controller.conflicts.isEmpty())
        assertTrue(controller.contents.entries.isEmpty())
    }

    @Test
    fun `a conflict that cannot be settled is added to the report`() {
        trash("2019", TrashAction.DEDUPE, "taken.jpg", "from trash")
        library("2019", "taken.jpg", "from library")
        controller.refresh(root)
        controller.toggleTab()
        controller.restore(root)
        val conflict = controller.conflicts.single()
        fs.delete(conflict.entry.handle)

        controller.resolve(root, conflict, ConflictChoice.BOTH)

        assertTrue(controller.report.last().contains("FAILED Trash/2019/Dedupe/taken.jpg"))
    }

    @Test
    fun `resolve all applies one choice to every waiting conflict`() {
        trash("2019", TrashAction.DEDUPE, "a.jpg", "trash a")
        trash("2019", TrashAction.DEDUPE, "b.jpg", "trash b")
        library("2019", "a.jpg", "lib a")
        library("2019", "b.jpg", "lib b")
        controller.refresh(root)
        controller.toggleTab()
        controller.restore(root)

        controller.resolveAll(root, ConflictChoice.LIBRARY_COPY)

        assertTrue(controller.conflicts.isEmpty())
        assertEquals("lib a", textOf("2019", "a.jpg"))
        assertEquals(setOf("a.jpg", "b.jpg"), namesIn("Trash", "2019", "Dedupe"))
    }
}
