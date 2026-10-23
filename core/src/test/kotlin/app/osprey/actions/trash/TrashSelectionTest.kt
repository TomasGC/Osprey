package app.osprey.actions.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashSelectionTest {

    private val lib = TrashTestLibrary()
    private val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
    private val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
    private val c = lib.trash("2020", TrashAction.VISUAL_DUPES, "c.jpg")
    private val contents = TrashScanner(lib.fs).scan(lib.root)

    @Test
    fun `starts empty`() {
        assertTrue(TrashSelection().isEmpty)
        assertFalse(a in TrashSelection())
    }

    @Test
    fun `toggle selects then unselects`() {
        val once = TrashSelection().toggle(a)
        assertTrue(a in once)
        assertFalse(b in once)
        assertTrue(a !in once.toggle(a))
    }

    @Test
    fun `select all in a tab selects only that tab and a second call unselects it`() {
        val tab = contents.entries("2019", TrashAction.DEDUPE)

        val all = TrashSelection().toggleAll(tab)

        assertEquals(setOf(a.label, b.label), all.labels)
        assertTrue(all.allSelected(tab))
        assertTrue(all.toggleAll(tab).isEmpty)
    }

    @Test
    fun `select all completes a partly selected tab`() {
        val tab = contents.entries("2019", TrashAction.DEDUPE)

        val all = TrashSelection().toggle(a).toggleAll(tab)

        assertTrue(all.allSelected(tab))
    }

    @Test
    fun `an empty tab is never all selected`() {
        assertFalse(TrashSelection().allSelected(emptyList()))
    }

    @Test
    fun `the selection persists across tabs`() {
        val selection = TrashSelection().toggle(a).toggle(c)

        assertEquals(listOf(a, c), selection.entriesIn(contents))
    }

    @Test
    fun `entries gone from the Trash drop out of the selection`() {
        val selection = TrashSelection().toggle(a).toggle(c)
        lib.fs.delete(a.handle)

        val fresh = TrashScanner(lib.fs).scan(lib.root)

        assertEquals(setOf(c.label), selection.retainedIn(fresh).labels)
    }
}
