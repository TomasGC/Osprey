package app.osprey.history

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-15T10:00:00Z")
private const val LIBRARY = "D:/Photos"

private fun file(path: String, result: FileResult) = FileRecord(path, result, "")

private fun run(
    state: RunState = RunState.Failed,
    origin: RunOrigin? = RunOrigin("dedupe", LIBRARY, "2021"),
    files: List<FileRecord> = listOf(file("2021/a.jpg", FileResult.Failed)),
    id: Long = 7,
) = RunRecord(id, "Dedupe apply", state, queuedAt = T0, endedAt = T0, files = files, origin = origin)

class RetryPlannerTest {

    @Test
    fun `the failed paths are the failed files only, not the skipped or the done`() {
        val files = listOf(
            file("2021/a.jpg", FileResult.Failed),
            file("2021/b.jpg", FileResult.Done),
            file("2021/c.jpg", FileResult.Skipped),
            file("2021/d.jpg", FileResult.Failed),
        )

        assertEquals(setOf("2021/a.jpg", "2021/d.jpg"), RetryPlanner.failedPaths(run(files = files)))
    }

    @Test
    fun `a failed run with failed files, a known kind and the same library and scope can be retried`() {
        assertTrue(RetryPlanner.canRetry(run(), LIBRARY, "2021"))
    }

    @Test
    fun `every known kind can be retried`() {
        RetryPlanner.kinds.forEach { kind ->
            assertTrue(kind, RetryPlanner.canRetry(run(origin = RunOrigin(kind, LIBRARY, "2021")), LIBRARY, "2021"))
        }
    }

    @Test
    fun `the button counts the failed files`() {
        val files = listOf(
            file("2021/a.jpg", FileResult.Failed),
            file("2021/b.jpg", FileResult.Done),
            file("2021/c.jpg", FileResult.Failed),
        )

        assertEquals("Retry failed (2)", RetryPlanner.retryLabel(run(files = files)))
    }

    @Test
    fun `the nine kinds are the ones the issue lists`() {
        assertEquals(
            listOf(
                "flatten", "rename", "dedupe", "dates", "standardize", "reorg", "trash-delete", "trash-restore",
                "visual-dupes",
            ),
            RetryPlanner.kinds,
        )
    }

    @Test
    fun `a run with no origin, an unknown kind or no failed file cannot be retried`() {
        assertFalse(RetryPlanner.canRetry(run(origin = null), LIBRARY, "2021"))
        assertFalse(RetryPlanner.canRetry(run(origin = RunOrigin("save-turn", LIBRARY, "2021")), LIBRARY, "2021"))
        assertFalse(
            RetryPlanner.canRetry(run(files = listOf(file("2021/a.jpg", FileResult.Done))), LIBRARY, "2021"),
        )
    }

    @Test
    fun `a stopped, interrupted, queued, running or done run cannot be retried`() {
        listOf(RunState.Stopped, RunState.Interrupted, RunState.Queued, RunState.Running, RunState.Done).forEach {
            assertFalse(it.name, RetryPlanner.canRetry(run(state = it), LIBRARY, "2021"))
        }
    }

    @Test
    fun `another library or another scope cannot be retried`() {
        assertFalse(RetryPlanner.canRetry(run(), "E:/Other", "2021"))
        assertFalse(RetryPlanner.canRetry(run(), LIBRARY, "2022"))
        assertFalse(RetryPlanner.canRetry(run(), LIBRARY, null))
    }

    @Test
    fun `a run with no scope is retried with no scope`() {
        val reorg = run(origin = RunOrigin("reorg", LIBRARY))

        assertTrue(RetryPlanner.canRetry(reorg, LIBRARY, null))
        assertFalse(RetryPlanner.canRetry(reorg, LIBRARY, "2021"))
    }

    @Test
    fun `the origin of a retry keeps its kind, library and scope and points back at the run`() {
        assertEquals(RunOrigin("dedupe", LIBRARY, "2021", retryOf = 7), RetryPlanner.retryOrigin(run()))
    }

    @Test
    fun `a run with no origin has no retry origin`() {
        assertNull(RetryPlanner.retryOrigin(run(origin = null)))
    }

    @Test
    fun `the summary of a retry says which run it retries`() {
        assertEquals(
            "Retry of run #7: Dedupe: 2 done, 0 failed",
            RetryPlanner.retrySummary(run(), "Dedupe: 2 done, 0 failed"),
        )
    }

    @Test
    fun `a failed file the new plan no longer holds is skipped with a reason, in path order`() {
        val missing = RetryPlanner.missingRecords(
            failed = setOf("2021/c.jpg", "2021/a.jpg", "2021/b.jpg"),
            planned = listOf("2021/b.jpg", "2021/x.jpg"),
        )

        assertEquals(
            listOf(
                FileRecord("2021/a.jpg", FileResult.Skipped, "no longer there or already handled"),
                FileRecord("2021/c.jpg", FileResult.Skipped, "no longer there or already handled"),
            ),
            missing,
        )
    }

    @Test
    fun `nothing is missing when the plan holds every failed file`() {
        assertEquals(emptyList<FileRecord>(), RetryPlanner.missingRecords(setOf("2021/a.jpg"), listOf("2021/a.jpg")))
    }
}
