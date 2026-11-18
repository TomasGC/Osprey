package app.osprey.actions.trash

import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.RecordingJobControl
import kotlin.random.Random
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

    @Test
    fun `records each restored file and each one that could not be`() {
        val gone = lib.trash("2019", TrashAction.DEDUPE, "gone.jpg")
        val b = lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        lib.fs.delete(gone.handle)
        val control = RecordingJobControl()

        val result = restorer.restore(lib.root, restorer.plan(lib.root, listOf(gone, b)), control)

        assertEquals(
            listOf(
                FileRecord("Trash/2019/Dedupe/gone.jpg", FileResult.Failed, result.failures.single().reason),
                FileRecord("Trash/2019/Dedupe/b.jpg", FileResult.Done, "restored"),
            ),
            control.files,
        )
    }

    @Test
    fun `a Reorg file whose identical twin is in the library is a conflict to settle`() {
        val a = lib.trash("2018", TrashAction.REORG, "y.jpg")
        lib.library("2018", "y.jpg")

        val plan = restorer.plan(lib.root, listOf(a))

        assertEquals(emptyList<TrashEntry>(), plan.direct)
        assertEquals(a, plan.conflicts.single().entry)
    }

    @Test
    fun `a Reorg file with no twin goes back into its year`() {
        val a = lib.trash("2018", TrashAction.REORG, "y.jpg")

        val result = restoreAll(a)

        assertEquals(1, result.restored)
        assertEquals(setOf("y.jpg"), lib.namesIn("2018"))
    }

    @Test
    fun `two files of one name and year come back, the later action suffixed`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "IMG_001.jpg", "dedupe".toByteArray())
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "IMG_001.jpg", "visual".toByteArray())

        val result = restoreAll(visual, dedupe) // ticked in the opposite order: the tab order decides, not the ticks

        assertEquals(2, result.restored)
        assertTrue(result.failures.isEmpty())
        assertEquals(setOf("IMG_001.jpg", "IMG_001_VisualDupes.jpg"), lib.namesIn("2021"))
        assertEquals("dedupe", textOf("2021", "IMG_001.jpg"))
        assertEquals("visual", textOf("2021", "IMG_001_VisualDupes.jpg"))
        assertFalse(lib.exists("Trash", "2021"))
    }

    @Test
    fun `three actions with one name keep the first and suffix the others`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")
        val reorg = lib.trash("2021", TrashAction.REORG, "a.jpg")

        restoreAll(reorg, visual, dedupe)

        assertEquals(setOf("a.jpg", "a_VisualDupes.jpg", "a_Reorg.jpg"), lib.namesIn("2021"))
    }

    @Test
    fun `the same name in two years is not a clash`() {
        val a = lib.trash("2020", TrashAction.DEDUPE, "a.jpg")
        val b = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")

        val plan = restorer.plan(lib.root, listOf(a, b))
        restorer.restore(lib.root, plan)

        assertTrue(plan.renames.isEmpty())
        assertEquals(setOf("a.jpg"), lib.namesIn("2020"))
        assertEquals(setOf("a.jpg"), lib.namesIn("2021"))
    }

    @Test
    fun `a name without an extension gets the suffix at its end`() {
        val a = lib.trash("2021", TrashAction.DEDUPE, "scan")
        val b = lib.trash("2021", TrashAction.VISUAL_DUPES, "scan")

        restoreAll(a, b)

        assertEquals(setOf("scan", "scan_VisualDupes"), lib.namesIn("2021"))
    }

    @Test
    fun `plan names the suffixed file and changes nothing`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")

        val plan = restorer.plan(lib.root, listOf(visual, dedupe))

        assertEquals(listOf(visual, dedupe).toSet(), plan.direct.toSet())
        assertEquals(mapOf(visual.label to "a_VisualDupes.jpg"), plan.renames)
        assertTrue(plan.conflicts.isEmpty())
        assertTrue(lib.exists("Trash", "2021", "VisualDupes", "a.jpg"))
    }

    @Test
    fun `a suffixed name the library already holds takes the next free dup name`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg", "visual".toByteArray())
        lib.library("2021", "a_VisualDupes.jpg", "library".toByteArray())

        val plan = restorer.plan(lib.root, listOf(dedupe, visual))
        restorer.restore(lib.root, plan)

        assertEquals(mapOf(visual.label to "a_VisualDupes__dup1.jpg"), plan.renames)
        assertEquals("library", textOf("2021", "a_VisualDupes.jpg"))
        assertEquals("visual", textOf("2021", "a_VisualDupes__dup1.jpg"))
    }

    @Test
    fun `a suffixed name another file of the same restore keeps is never taken from it`() {
        val plain = lib.trash("2021", TrashAction.DEDUPE, "a_VisualDupes.jpg", "plain".toByteArray())
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg", "visual".toByteArray())

        val plan = restorer.plan(lib.root, listOf(visual, dedupe, plain))
        restorer.restore(lib.root, plan)

        assertEquals(mapOf(visual.label to "a_VisualDupes__dup1.jpg"), plan.renames)
        assertEquals("plain", textOf("2021", "a_VisualDupes.jpg"))
        assertEquals("visual", textOf("2021", "a_VisualDupes__dup1.jpg"))
    }

    @Test
    fun `files that meet a library file are all conflicts and none is suffixed`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")
        lib.library("2021", "a.jpg")

        val plan = restorer.plan(lib.root, listOf(dedupe, visual))

        assertTrue(plan.direct.isEmpty())
        assertTrue(plan.renames.isEmpty())
        assertEquals(setOf(dedupe, visual), plan.conflicts.map { it.entry }.toSet())
    }

    @Test
    fun `a same-name pair is restored suffixed while another file that meets the library stays a conflict`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg", "dedupe".toByteArray())
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg", "visual".toByteArray())
        val taken = lib.trash("2021", TrashAction.DEDUPE, "b.jpg", "trash b".toByteArray())
        lib.library("2021", "b.jpg", "library b".toByteArray())

        val plan = restorer.plan(lib.root, listOf(dedupe, visual, taken))
        val result = restorer.restore(lib.root, plan)

        assertEquals(setOf(dedupe, visual), plan.direct.toSet())
        assertEquals(mapOf(visual.label to "a_VisualDupes.jpg"), plan.renames)
        assertEquals(listOf(taken), plan.conflicts.map { it.entry })
        assertEquals(2, result.restored)
        assertEquals(setOf("a.jpg", "a_VisualDupes.jpg", "b.jpg"), lib.namesIn("2021"))
        assertEquals("library b", textOf("2021", "b.jpg"))
        assertEquals("trash b", textOf("Trash", "2021", "Dedupe", "b.jpg"))
    }

    @Test
    fun `a stop after the first file leaves the suffixed one in the Trash`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")

        val plan = restorer.plan(lib.root, listOf(dedupe, visual))
        val result = restorer.restore(lib.root, plan, control = RecordingJobControl(stopAfterReports = 1))

        assertTrue(result.cancelled)
        assertEquals(setOf("a.jpg"), lib.namesIn("2021"))
        assertEquals(setOf("a.jpg"), lib.namesIn("Trash", "2021", "VisualDupes"))
    }

    @Test
    fun `a suffixed file that cannot be moved stays in the Trash and the plain one is restored`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")
        val faulty = FaultyFileSystemRepository(lib.fs)
        faulty.failMove = { entry -> if (entry == visual.handle) FileAlreadyExistsException("taken") else null }
        val faultyRestorer = TrashRestorer(faulty)

        val result = faultyRestorer.restore(lib.root, faultyRestorer.plan(lib.root, listOf(dedupe, visual)))

        assertEquals(1, result.restored)
        assertEquals(listOf(visual.label), result.failures.map { it.label })
        assertEquals(setOf("a.jpg"), lib.namesIn("2021"))
        assertTrue(lib.exists("Trash", "2021", "VisualDupes", "a.jpg"))
    }

    @Test
    fun `records the name a suffixed file was restored as`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")
        val control = RecordingJobControl()

        restorer.restore(lib.root, restorer.plan(lib.root, listOf(dedupe, visual)), control)

        assertEquals(
            listOf(
                FileRecord("Trash/2021/Dedupe/a.jpg", FileResult.Done, "restored"),
                FileRecord("Trash/2021/VisualDupes/a.jpg", FileResult.Done, "restored as a_VisualDupes.jpg"),
            ),
            control.files,
        )
    }

    @Test
    fun `whatever the mix of names, a restore never gives two files one name nor overwrites the library`() {
        val names = listOf("a.jpg", "a_VisualDupes.jpg", "a_Reorg.jpg", "a_VisualDupes__dup1.jpg")
        val random = Random(85)
        repeat(300) { round ->
            val library = TrashTestLibrary()
            val inLibrary = names.filter { random.nextInt(10) < 3 }
            inLibrary.forEach { library.library("2021", it) }
            val entries = TrashAction.entries.flatMap { action ->
                names.filter { random.nextInt(10) < 4 }.map { library.trash("2021", action, it) }
            }
            val restorer = TrashRestorer(library.fs)

            val plan = restorer.plan(library.root, entries)
            val result = restorer.restore(library.root, plan)

            val targets = plan.direct.map { plan.renames[it.label] ?: it.name }
            assertEquals("round $round: targets", targets.size, targets.toSet().size)
            assertTrue("round $round: library", targets.none { it in inLibrary })
            assertEquals("round $round: split", entries.size, plan.direct.size + plan.conflicts.size)
            assertEquals("round $round: restored", plan.direct.size, result.restored)
            assertTrue("round $round: failures", result.failures.isEmpty())
            assertEquals("round $round: names", inLibrary.size + plan.direct.size, library.namesIn("2021").size)
        }
    }
}
