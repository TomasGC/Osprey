package app.osprey.history

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val T0: Instant = Instant.parse("2019-05-04T12:00:00Z")

class RunTextTest {

    private fun run(
        state: RunState,
        summary: String = "",
        startedAt: Instant? = null,
        endedAt: Instant? = null,
        files: List<FileRecord> = emptyList(),
    ) = RunRecord(1, "Reorg", state, T0, startedAt, endedAt, summary, files = files)

    private fun took(seconds: Long) = run(RunState.Done, startedAt = T0, endedAt = T0.plusSeconds(seconds))

    private fun file(result: FileResult) = FileRecord("a.jpg", result)

    @Test
    fun `a waiting run says it waits`() {
        assertEquals("Waiting for the jobs before it", runSubtitle(run(RunState.Queued)))
    }

    @Test
    fun `a running run says it works`() {
        assertEquals("Working...", runSubtitle(run(RunState.Running)))
    }

    @Test
    fun `an interrupted run says the app closed`() {
        assertEquals("Interrupted: the app closed before it finished", runSubtitle(run(RunState.Interrupted)))
    }

    @Test
    fun `a run that ended says its summary`() {
        for (state in listOf(RunState.Done, RunState.Failed, RunState.Stopped)) {
            assertEquals("Reorg: 1 done", runSubtitle(run(state, summary = "Reorg: 1 done")))
        }
    }

    @Test
    fun `a run that has not both started and ended has no duration`() {
        assertNull(runDurationText(run(RunState.Queued)))
        assertNull(runDurationText(run(RunState.Running, startedAt = T0)))
        assertNull(runDurationText(run(RunState.Interrupted, endedAt = T0)))
    }

    @Test
    fun `durations are written in the largest units that matter`() {
        assertEquals("under 1 s", runDurationText(took(0)))
        assertEquals("1 s", runDurationText(took(1)))
        assertEquals("5 s", runDurationText(took(5)))
        assertEquals("1 min 5 s", runDurationText(took(65)))
        assertEquals("2 min", runDurationText(took(120)))
        assertEquals("1 h 2 min", runDurationText(took(3720)))
        assertEquals("1 h", runDurationText(took(3600)))
    }

    @Test
    fun `a run that started says when it started, in the given zone`() {
        val started = run(RunState.Running, startedAt = T0.plusSeconds(5))

        assertEquals("2019-05-04 12:00:05", runWhenText(started, ZoneOffset.UTC))
        assertEquals("2019-05-04 14:00:05", runWhenText(started, ZoneOffset.ofHours(2)))
    }

    @Test
    fun `a run that never started says when it was queued`() {
        val interrupted = run(RunState.Interrupted, endedAt = T0.plusSeconds(9))

        assertEquals("2019-05-04 12:00:00", runWhenText(run(RunState.Queued), ZoneOffset.UTC))
        assertEquals("2019-05-04 12:00:00", runWhenText(interrupted, ZoneOffset.UTC))
    }

    @Test
    fun `a run with no file says so`() {
        assertEquals("No files recorded", runCounts(run(RunState.Done)))
    }

    @Test
    fun `one file is counted in the singular`() {
        assertEquals("1 file: 1 done", runCounts(run(RunState.Done, files = listOf(file(FileResult.Done)))))
    }

    @Test
    fun `counts name each result that happened and leave out the others`() {
        val files = listOf(
            file(FileResult.Done),
            file(FileResult.Failed),
            file(FileResult.Done),
            file(FileResult.Skipped),
        )

        assertEquals("4 files: 2 done, 1 failed, 1 skipped", runCounts(run(RunState.Failed, files = files)))
        assertEquals("1 file: 1 failed", runCounts(run(RunState.Failed, files = listOf(file(FileResult.Failed)))))
    }

    @Test
    fun `a run page groups its files with the failures first`() {
        val files = listOf(
            FileRecord("1.jpg", FileResult.Done),
            FileRecord("2.jpg", FileResult.Failed, "locked"),
            FileRecord("3.jpg", FileResult.Skipped),
            FileRecord("4.jpg", FileResult.Done),
            FileRecord("5.jpg", FileResult.Failed),
        )

        val groups = runFileGroups(run(RunState.Failed, files = files))

        assertEquals(listOf(FileResult.Failed, FileResult.Skipped, FileResult.Done), groups.map { it.result })
        assertEquals(listOf("2.jpg", "5.jpg"), groups[0].files.map { it.path })
        assertEquals(listOf("3.jpg"), groups[1].files.map { it.path })
        assertEquals(listOf("1.jpg", "4.jpg"), groups[2].files.map { it.path })
        assertEquals(listOf("Failed (2)", "Skipped (1)", "Done (2)"), groups.map { it.title })
    }

    @Test
    fun `a result no file ended with has no group`() {
        val groups = runFileGroups(run(RunState.Done, files = listOf(file(FileResult.Done))))

        assertEquals(listOf(FileResult.Done), groups.map { it.result })
    }

    @Test
    fun `a run with no file has no group`() {
        assertEquals(emptyList<RunFileGroup>(), runFileGroups(run(RunState.Done)))
    }

    @Test
    fun `a file line is its path then how it went`() {
        assertEquals("a.jpg: moved to b", fileLine(FileRecord("a.jpg", FileResult.Done, "moved to b")))
        assertEquals("a.jpg", fileLine(FileRecord("a.jpg", FileResult.Done)))
        assertEquals("a.jpg: FAILED: locked", fileLine(FileRecord("a.jpg", FileResult.Failed, "locked")))
        assertEquals("a.jpg: FAILED", fileLine(FileRecord("a.jpg", FileResult.Failed)))
        assertEquals("a.jpg: skipped: gone", fileLine(FileRecord("a.jpg", FileResult.Skipped, "gone")))
    }

    @Test
    fun `before and after name, size, dates and hash in the given zone`() {
        val record = FileRecord(
            "a (1).jpg",
            FileResult.Done,
            before = FileState(7, T0, T0.plusSeconds(5), "abc", "a (1).jpg"),
            after = FileState(2048, name = "a.jpg"),
        )

        assertEquals(
            listOf(
                "before: a (1).jpg, 7 B, created 2019-05-04 12:00:00, modified 2019-05-04 12:00:05, hash abc",
                "after: a.jpg, 2 KB",
            ),
            fileChangeLines(record, ZoneOffset.UTC),
        )
        assertEquals(
            "before: a (1).jpg, 7 B, created 2019-05-04 14:00:00, modified 2019-05-04 14:00:05, hash abc",
            fileChangeLines(record, ZoneOffset.ofHours(2)).first(),
        )
    }

    @Test
    fun `a file with only a state before or after gives one line, none gives none`() {
        val only = FileRecord("a.jpg", FileResult.Failed, before = FileState(7))

        assertEquals(listOf("before: 7 B"), fileChangeLines(only, ZoneOffset.UTC))
        assertEquals(emptyList<String>(), fileChangeLines(file(FileResult.Done), ZoneOffset.UTC))
    }
}
