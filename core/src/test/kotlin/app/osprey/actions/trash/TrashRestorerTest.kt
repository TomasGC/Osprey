package app.osprey.actions.trash

import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.jobs.RecordingJobControl
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

        val plan = restorer.plan(lib.root, listOf(a, b))
        val result = restorer.restore(lib.root, plan, control = RecordingJobControl(stopAfterReports = 1))

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

    @Test
    fun `keeping the Trash copy of a file that vanished from the Trash reports it and changes nothing`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg")
        lib.library("2019", "taken.jpg", "library".toByteArray())
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()
        lib.fs.delete(taken.handle)

        val failure = restorer.resolve(lib.root, conflict, ConflictChoice.TRASH_COPY)

        assertEquals("Trash/2019/Dedupe/taken.jpg", failure?.label)
        assertEquals("not in the Trash any more", failure?.reason)
        assertEquals("library", textOf("2019", "taken.jpg"))
    }

    @Test
    fun `keeping the Trash copy puts the library file back when the swap fails`() {
        val taken = lib.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
        lib.library("2019", "taken.jpg", "library".toByteArray())
        val conflict = restorer.plan(lib.root, listOf(taken)).conflicts.single()
        val faulty = FaultyFileSystemRepository(lib.fs)
        faulty.failMove = { entry -> if (entry == taken.handle) FileAlreadyExistsException("taken.jpg appeared") else null }

        val failure = TrashRestorer(faulty).resolve(lib.root, conflict, ConflictChoice.TRASH_COPY)

        assertEquals("taken.jpg appeared", failure?.reason)
        assertEquals("library", textOf("2019", "taken.jpg"))
        assertEquals("trash", textOf("Trash", "2019", "Dedupe", "taken.jpg"))
        assertTrue(lib.namesIn("Trash", "2019", "Replaced").isEmpty())
    }

    @Test
    fun `restoring into a year that exists as a file reports the failure and keeps the Trash file`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.fs.addFile(lib.root, "2019")

        val result = restoreAll(a)

        assertEquals(0, result.restored)
        assertEquals(listOf("Trash/2019/Dedupe/a.jpg"), result.failures.map { it.label })
        assertTrue(lib.exists("Trash", "2019", "Dedupe", "a.jpg"))
    }

    @Test
    fun `every choice settles a conflict without a failure`() {
        for (choice in ConflictChoice.entries) {
            val library = TrashTestLibrary()
            val taken = library.trash("2019", TrashAction.DEDUPE, "taken.jpg", "trash".toByteArray())
            library.library("2019", "taken.jpg", "library".toByteArray())
            val conflict = TrashRestorer(library.fs).plan(library.root, listOf(taken)).conflicts.single()

            assertNull("$choice", TrashRestorer(library.fs).resolve(library.root, conflict, choice))
        }
    }
}
