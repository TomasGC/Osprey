package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashRestorerTest {

    private val lib = TrashTestLibrary()
    private val restorer = TrashRestorer(lib.fs)

    private fun restoreAll(vararg entries: TrashEntry): TrashRestoreResult {
        val plan = restorer.plan(lib.root, entries.toList())
        return restorer.restore(lib.root, plan)
    }

    private fun textOf(vararg path: String): String {
        var folder: FileHandle = lib.root
        for (segment in path.dropLast(1)) {
            folder = lib.fs.list(folder).first { it.isDirectory && it.name == segment }.handle
        }
        val file = lib.fs.list(folder).first { it.name == path.last() }.handle
        return String(lib.fs.readBytes(file))
    }

    @Test
    fun `restores into the year folder it came from`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.library("2019", "existing.jpg")

        val result = restoreAll(a)

        assertEquals(1, result.restored)
        assertEquals(setOf("existing.jpg", "a.jpg"), lib.namesIn("2019"))
        assertFalse(lib.exists("Trash", "2019"))
        assertTrue(lib.exists("Trash"))
    }

    @Test
    fun `recreates a year folder that is gone`() {
        val a = lib.trash("2018", TrashAction.VISUAL_DUPES, "a.jpg")

        restoreAll(a)

        assertEquals(setOf("a.jpg"), lib.namesIn("2018"))
    }

    @Test
    fun `plan splits free names from conflicts and changes nothing`() {
        val free = lib.trash("2019", TrashAction.DEDUPE, "free.jpg")
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())

        val plan = restorer.plan(lib.root, listOf(free, taken))

        assertEquals(listOf("free.jpg"), plan.direct.map { it.name })
        assertEquals(listOf("taken.jpg"), plan.conflicts.map { it.entry.name })
        assertEquals(7L, plan.conflicts.single().existing.sizeBytes)
        assertTrue(lib.exists("Trash", "2019", "Dedupe", "taken.jpg"))
    }

    @Test
    fun `restore never touches a conflict`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())

        val result = restoreAll(taken)

        assertEquals(0, result.restored)
        assertEquals("library", textOf("2019", "taken.jpg"))
        assertTrue(lib.exists("Trash", "2019", "Dedupe", "taken.jpg"))
    }

    @Test
    fun `a stop leaves the remaining files in the Trash`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        var calls = 0

        val plan = restorer.plan(lib.root, listOf(a, b))
        val result = restorer.restore(lib.root, plan, shouldStop = { calls++ >= 1 })

        assertTrue(result.cancelled)
        assertEquals(1, result.restored)
        assertEquals(1, lib.namesIn("Trash", "2019", "Dedupe").size)
    }

    @Test
    fun `a file already gone is reported and the run goes on`() {
        val gone = lib.trash("2019", TrashAction.DEDUPE, "gone.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        lib.fs.delete(gone.handle)

        val result = restoreAll(gone, b)

        assertEquals(1, result.restored)
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `keeping the Trash copy swaps the library file into Replaced`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()

        val failure = restorer.resolve(lib.root, conflict, ConflictChoice.TRASH_COPY)

        assertNull(failure)
        assertEquals("trash", textOf("2019", "taken.jpg"))
        assertEquals("library", textOf("Trash", "2019", "Replaced", "taken.jpg"))
        assertFalse(lib.exists("Trash", "2019", "Dedupe"))
    }

    @Test
    fun `keeping the library copy leaves both files where they are`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()

        val failure = restorer.resolve(lib.root, conflict, ConflictChoice.LIBRARY_COPY)

        assertNull(failure)
        assertEquals("library", textOf("2019", "taken.jpg"))
        assertEquals("trash", textOf("Trash", "2019", "Dedupe", "taken.jpg"))
    }

    @Test
    fun `keeping both restores the Trash file under a free dup name`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())
        lib.library("2019", "taken__dup1.jpg")
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()

        val failure = restorer.resolve(lib.root, conflict, ConflictChoice.BOTH)

        assertNull(failure)
        assertEquals("library", textOf("2019", "taken.jpg"))
        assertEquals("trash", textOf("2019", "taken__dup2.jpg"))
        assertFalse(lib.exists("Trash", "2019"))
    }

    @Test
    fun `resolving a conflict whose Trash file vanished reports it`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg")
        lib.library("2019", "taken.jpg")
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()
        lib.fs.delete(taken.handle)

        val failure = restorer.resolve(lib.root, conflict, ConflictChoice.BOTH)

        assertEquals("Trash/2019/Dedupe/taken.jpg", failure?.label)
    }
}
