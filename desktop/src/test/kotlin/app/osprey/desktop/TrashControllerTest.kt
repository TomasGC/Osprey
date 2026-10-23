package app.osprey.desktop

import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.TrashAction
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrashControllerTest {

    private lateinit var library: Path
    private lateinit var controller: TrashController

    private val root get() = PathHandle(library)

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-trash-controller-test")
        controller = TrashController(DesktopFileSystemRepository())
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun trash(year: String, action: String, name: String, text: String = name) {
        val folder = Files.createDirectories(library.resolve("Trash").resolve(year).resolve(action))
        Files.writeString(folder.resolve(name), text)
    }

    private fun entry(year: String, action: TrashAction, name: String) =
        controller.contents.entries(year, action).single { it.name == name }

    @Test
    fun `refresh lists the Trash and opens the first year and action`() {
        trash("2020", "Dedupe", "b.jpg")
        trash("2019", "VisualDupes", "a.jpg")

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
        trash("2019", "Dedupe", "a.jpg")
        trash("2020", "Dedupe", "b.jpg")
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
        trash("2019", "Dedupe", "a.jpg")
        trash("2019", "Dedupe", "b.jpg")
        trash("2019", "VisualDupes", "c.jpg")
        controller.refresh(root)

        controller.toggleTab()

        assertEquals(2, controller.selection.labels.size)
        assertFalse(entry("2019", TrashAction.VISUAL_DUPES, "c.jpg") in controller.selection)
        assertTrue(controller.tabFullySelected)
    }

    @Test
    fun `delete asks first and changes nothing until confirmed`() {
        trash("2019", "Dedupe", "a.jpg")
        controller.refresh(root)
        controller.toggleTab()

        controller.requestDelete()

        assertEquals(
            "Permanently delete 1 file (5 B) from Trash/2019/Dedupe? This cannot be undone.",
            controller.confirmation,
        )
        assertTrue(Files.exists(library.resolve("Trash/2019/Dedupe/a.jpg")))
    }

    @Test
    fun `delete with nothing selected asks nothing`() {
        trash("2019", "Dedupe", "a.jpg")
        controller.refresh(root)

        controller.requestDelete()

        assertNull(controller.confirmation)
    }

    @Test
    fun `dismissing the question deletes nothing`() {
        trash("2019", "Dedupe", "a.jpg")
        controller.refresh(root)
        controller.toggleTab()
        controller.requestDelete()

        controller.confirmation = null

        assertNull(controller.confirmation)
        assertTrue(Files.exists(library.resolve("Trash/2019/Dedupe/a.jpg")))
    }

    @Test
    fun `confirming deletes exactly the selection and refreshes`() {
        trash("2019", "Dedupe", "a.jpg")
        trash("2019", "Dedupe", "keep.jpg")
        trash("2020", "Dedupe", "other.jpg")
        controller.refresh(root)
        controller.toggle(entry("2019", TrashAction.DEDUPE, "a.jpg"))
        controller.requestDelete()

        runBlocking { controller.confirmDelete(root) }

        assertFalse(Files.exists(library.resolve("Trash/2019/Dedupe/a.jpg")))
        assertTrue(Files.exists(library.resolve("Trash/2019/Dedupe/keep.jpg")))
        assertTrue(Files.exists(library.resolve("Trash/2020/Dedupe/other.jpg")))
        assertEquals(2, controller.contents.entries.size)
        assertTrue(controller.selection.isEmpty)
        assertNull(controller.confirmation)
        assertTrue(controller.report.first().contains("DELETED 1"))
    }

    @Test
    fun `restore moves the selection back and keeps conflicts for a decision`() {
        trash("2019", "Dedupe", "free.jpg")
        trash("2019", "Dedupe", "taken.jpg", "from trash")
        Files.createDirectories(library.resolve("2019"))
        Files.writeString(library.resolve("2019/taken.jpg"), "from library")
        controller.refresh(root)
        controller.toggleTab()

        runBlocking { controller.restore(root) }

        assertTrue(Files.exists(library.resolve("2019/free.jpg")))
        assertEquals("from library", Files.readString(library.resolve("2019/taken.jpg")))
        assertEquals(listOf("taken.jpg"), controller.conflicts.map { it.entry.name })
    }

    @Test
    fun `resolving a conflict applies the choice and refreshes`() {
        trash("2019", "Dedupe", "taken.jpg", "from trash")
        Files.createDirectories(library.resolve("2019"))
        Files.writeString(library.resolve("2019/taken.jpg"), "from library")
        controller.refresh(root)
        controller.toggleTab()
        runBlocking { controller.restore(root) }

        controller.resolve(root, controller.conflicts.single(), ConflictChoice.BOTH)

        assertEquals("from trash", Files.readString(library.resolve("2019/taken__dup1.jpg")))
        assertTrue(controller.conflicts.isEmpty())
        assertTrue(controller.contents.entries.isEmpty())
    }

    @Test
    fun `resolve all applies one choice to every waiting conflict`() {
        trash("2019", "Dedupe", "a.jpg", "trash a")
        trash("2019", "Dedupe", "b.jpg", "trash b")
        Files.createDirectories(library.resolve("2019"))
        Files.writeString(library.resolve("2019/a.jpg"), "lib a")
        Files.writeString(library.resolve("2019/b.jpg"), "lib b")
        controller.refresh(root)
        controller.toggleTab()
        runBlocking { controller.restore(root) }

        controller.resolveAll(root, ConflictChoice.LIBRARY_COPY)

        assertTrue(controller.conflicts.isEmpty())
        assertEquals("lib a", Files.readString(library.resolve("2019/a.jpg")))
        assertTrue(Files.exists(library.resolve("Trash/2019/Dedupe/a.jpg")))
    }
}
