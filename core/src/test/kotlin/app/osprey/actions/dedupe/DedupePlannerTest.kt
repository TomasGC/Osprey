package app.osprey.actions.dedupe

import app.osprey.jobs.JobProgress
import app.osprey.jobs.RecordingJobControl
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupePlannerTest {

    private val fs = FakeFileSystemRepository()
    private val year: FileHandle = fs.addFolder(fs.root, "2019")
    private val trash = TrashLocation(fs.root, "2019")
    private val planner = DedupePlanner(fs)

    private fun trashFolder(): FileHandle = TrashFolders(fs).ensure(trash, TrashAction.DEDUPE)

    @Test
    fun `plan finds nothing when no files duplicate`() {
        fs.addFile(year, "a.jpg", "content one".toByteArray())
        fs.addFile(year, "b.jpg", "content two".toByteArray())

        val plan = planner.plan(year, trash)

        assertEquals(emptyList<DedupeMove>(), plan.moves)
    }

    @Test
    fun `plan does not flag same-size files with different content`() {
        fs.addFile(year, "a.jpg", "aaaaa".toByteArray())
        fs.addFile(year, "b.jpg", "bbbbb".toByteArray())

        val plan = planner.plan(year, trash)

        assertEquals(emptyList<DedupeMove>(), plan.moves)
    }

    @Test
    fun `plan keeps the shortest, then alphabetically-first, name`() {
        fs.addFile(year, "zzzzz.jpg", "dup content".toByteArray())
        fs.addFile(year, "a.jpg", "dup content".toByteArray())
        fs.addFile(year, "bb.jpg", "dup content".toByteArray())

        val plan = planner.plan(year, trash)

        assertEquals(2, plan.moves.size)
        assertEquals(setOf("a.jpg"), plan.moves.map { it.keepName }.toSet())
        assertEquals(setOf("zzzzz.jpg", "bb.jpg"), plan.moves.map { it.sourceName }.toSet())
    }

    @Test
    fun `plan groups a three-way duplicate together`() {
        for (name in listOf("a.jpg", "b.jpg", "c.jpg")) {
            fs.addFile(year, name, "same".toByteArray())
        }

        val plan = planner.plan(year, trash)

        assertEquals(2, plan.moves.size)
        assertTrue(plan.moves.all { it.keepName == "a.jpg" })
    }

    @Test
    fun `plan suffixes a destination name already taken in the Dedupe trash folder`() {
        fs.addFile(trashFolder(), "b.jpg", "pre-existing".toByteArray())
        fs.addFile(year, "a.jpg", "dup content".toByteArray())
        fs.addFile(year, "b.jpg", "dup content".toByteArray())

        val plan = planner.plan(year, trash)

        assertEquals(1, plan.moves.size)
        assertEquals("b__dup1.jpg", plan.moves.single().destName)
    }

    @Test
    fun `plan ignores a legacy TODELETE folder inside the year`() {
        fs.addFile(fs.addFolder(year, "TODELETE"), "b.jpg", "pre-existing".toByteArray())
        fs.addFile(year, "a.jpg", "dup content".toByteArray())
        fs.addFile(year, "b.jpg", "dup content".toByteArray())

        val plan = planner.plan(year, trash)

        assertEquals("b.jpg", plan.moves.single().destName)
    }

    @Test
    fun `apply moves extras into Trash, year, Dedupe and keeps the original`() {
        fs.addFile(year, "a.jpg", "dup content".toByteArray())
        fs.addFile(year, "b.jpg", "dup content".toByteArray())
        val plan = planner.plan(year, trash)

        planner.apply(plan, trash)

        assertTrue(fs.list(year).any { it.name == "a.jpg" })
        assertFalse(fs.list(year).any { it.name == "b.jpg" })
        assertEquals(listOf("b.jpg"), fs.list(trashFolder()).map { it.name })
        assertTrue(fs.list(year).none { it.isDirectory })
    }

    @Test
    fun `apply on a no-op plan never creates Trash`() {
        fs.addFile(year, "a.jpg", "unique".toByteArray())
        val plan = planner.plan(year, trash)

        planner.apply(plan, trash)

        assertFalse(fs.list(fs.root).any { it.name == "Trash" })
    }

    @Test
    fun `a second run plans nothing`() {
        fs.addFile(year, "a.jpg", "dup content".toByteArray())
        fs.addFile(year, "b.jpg", "dup content".toByteArray())
        planner.apply(planner.plan(year, trash), trash)

        assertEquals(emptyList<DedupeMove>(), planner.plan(year, trash).moves)
    }

    @Test
    fun `describe logs each move and counts them`() {
        val plan = DedupePlan(
            moves = listOf(DedupeMove(fs.addFile(year, "b.jpg"), "b.jpg", "b.jpg", "a.jpg")),
        )
        val logs = mutableListOf<String>()

        val count = planner.describe(plan, logs::add)

        assertEquals(1, count)
        assertEquals("  TRASH b.jpg  (dup of a.jpg)", logs.single())
    }

    private fun twoDuplicatePairs() {
        fs.addFile(year, "a.jpg", byteArrayOf(1, 2, 3))
        fs.addFile(year, "bb.jpg", byteArrayOf(1, 2, 3))
        fs.addFile(year, "c.jpg", byteArrayOf(9, 9))
        fs.addFile(year, "dd.jpg", byteArrayOf(9, 9))
        fs.addFile(year, "solo.jpg", byteArrayOf(7))
    }

    @Test
    fun `plan reports each file it has to read, never one with a size of its own`() {
        twoDuplicatePairs()
        val control = RecordingJobControl()

        planner.plan(year, trash, control)

        assertEquals(setOf("a.jpg", "bb.jpg", "c.jpg", "dd.jpg"), control.reports.map { it.current }.toSet())
        assertEquals(setOf(4), control.reports.map { it.total }.toSet())
    }

    @Test
    fun `a scan stopped before every file was read plans nothing`() {
        twoDuplicatePairs()

        val plan = planner.plan(year, trash, RecordingJobControl(stopAfterReports = 3))

        assertEquals(emptyList<DedupeMove>(), plan.moves)
    }

    @Test
    fun `apply reports each move and returns how many it made`() {
        twoDuplicatePairs()
        val control = RecordingJobControl()

        val moved = planner.apply(planner.plan(year, trash), trash, control)

        assertEquals(2, moved)
        assertEquals(setOf("bb.jpg", "dd.jpg"), control.reports.map { it.current }.toSet())
    }

    @Test
    fun `a stopped apply leaves the other duplicate and a new scan finds it`() {
        twoDuplicatePairs()

        val moved = planner.apply(planner.plan(year, trash), trash, RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, moved)
        assertEquals(1, planner.plan(year, trash).moves.size)
    }

    @Test
    fun `an empty plan moves nothing and reports nothing`() {
        val control = RecordingJobControl()

        assertEquals(0, planner.apply(DedupePlan(emptyList()), trash, control))
        assertEquals(emptyList<JobProgress>(), control.reports)
    }
}
