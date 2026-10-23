package app.osprey.actions.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashClearerTest {

    private val lib = TrashTestLibrary()

    private fun clear(entries: Collection<TrashEntry>, shouldStop: () -> Boolean = { false }) =
        TrashClearer(lib.fs).clear(lib.root, entries, shouldStop)

    @Test
    fun `deletes exactly the given files and nothing else`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.DEDUPE, "sibling.jpg")
        lib.trash("2019", TrashAction.VISUAL_DUPES, "other-action.jpg")
        lib.trash("2020", TrashAction.DEDUPE, "other-year.jpg")
        lib.library("2019", "library.jpg")

        val result = clear(listOf(a))

        assertEquals(1, result.deleted)
        assertEquals(setOf("sibling.jpg"), lib.namesIn("Trash", "2019", "Dedupe"))
        assertEquals(setOf("other-action.jpg"), lib.namesIn("Trash", "2019", "VisualDupes"))
        assertEquals(setOf("other-year.jpg"), lib.namesIn("Trash", "2020", "Dedupe"))
        assertEquals(setOf("library.jpg"), lib.namesIn("2019"))
    }

    @Test
    fun `removes an emptied action folder and an emptied year folder but never Trash`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val b = lib.trash("2019", TrashAction.VISUAL_DUPES, "b.jpg")

        val result = clear(listOf(a, b))

        assertEquals(3, result.prunedFolders)
        assertFalse(lib.exists("Trash", "2019"))
        assertTrue(lib.exists("Trash"))
    }

    @Test
    fun `keeps a year folder while another action still holds files`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.VISUAL_DUPES, "b.jpg")

        val result = clear(listOf(a))

        assertEquals(1, result.prunedFolders)
        assertFalse(lib.exists("Trash", "2019", "Dedupe"))
        assertTrue(lib.exists("Trash", "2019", "VisualDupes", "b.jpg"))
    }

    @Test
    fun `an action folder that still has a file is kept`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.DEDUPE, "b.jpg")

        val result = clear(listOf(a))

        assertEquals(0, result.prunedFolders)
        assertTrue(lib.exists("Trash", "2019", "Dedupe", "b.jpg"))
    }

    @Test
    fun `a file outside the Trash is never deleted even if handed in`() {
        val outside = lib.library("2019", "photo.jpg")
        val forged = TrashEntry("2019", TrashAction.DEDUPE, outside, "photo.jpg", 3)

        val result = clear(listOf(forged))

        assertEquals(0, result.deleted)
        assertEquals(1, result.failures.size)
        assertTrue(lib.exists("2019", "photo.jpg"))
    }

    @Test
    fun `a file already gone is reported and the run goes on`() {
        val gone = lib.trash("2019", TrashAction.DEDUPE, "gone.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        lib.fs.delete(gone.handle)

        val result = clear(listOf(gone, b))

        assertEquals(1, result.deleted)
        assertEquals(listOf("Trash/2019/Dedupe/gone.jpg"), result.failures.map { it.label })
    }

    @Test
    fun `stops between two files when asked and keeps the rest`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        var calls = 0

        val result = clear(listOf(a, b), shouldStop = { calls++ >= 1 })

        assertTrue(result.cancelled)
        assertEquals(1, result.deleted)
        assertEquals(1, lib.namesIn("Trash", "2019", "Dedupe").size)
    }

    @Test
    fun `reports progress per file`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        val seen = mutableListOf<TrashProgress>()

        TrashClearer(lib.fs).clear(lib.root, listOf(a, b), progress = { seen += it })

        assertEquals(listOf(0, 1), seen.map { it.done })
        assertTrue(seen.all { it.total == 2 })
    }
}
