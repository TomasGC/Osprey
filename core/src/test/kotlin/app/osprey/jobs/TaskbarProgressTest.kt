package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskbarProgressTest {

    private var nextId = 1L

    private fun entry(state: JobState) = JobEntry(nextId++, "Job", state)

    private fun finished(outcome: JobOutcome) = entry(JobState.Finished(outcome))

    @Test
    fun `no job shows nothing`() {
        assertEquals(TaskbarProgress(TaskbarState.NONE, 0), taskbarProgress(emptyList()))
    }

    @Test
    fun `a running job with no file reported yet shows an unknown amount`() {
        assertEquals(
            TaskbarProgress(TaskbarState.INDETERMINATE, 0),
            taskbarProgress(listOf(entry(JobState.Running()))),
        )
    }

    @Test
    fun `a running job shows the share of files handled`() {
        val running = entry(JobState.Running(JobProgress(3, 10, "a.jpg")))

        assertEquals(TaskbarProgress(TaskbarState.NORMAL, 30), taskbarProgress(listOf(running)))
    }

    @Test
    fun `a job that is only waiting shows an unknown amount`() {
        assertEquals(TaskbarProgress(TaskbarState.INDETERMINATE, 0), taskbarProgress(listOf(entry(JobState.Queued))))
    }

    @Test
    fun `the running job is what shows, not the ones behind it`() {
        val entries = listOf(entry(JobState.Running(JobProgress(5, 10, "a.jpg"))), entry(JobState.Queued))

        assertEquals(TaskbarProgress(TaskbarState.NORMAL, 50), taskbarProgress(entries))
    }

    @Test
    fun `a finished job with failures shows an error`() {
        val entries = listOf(finished(JobOutcome("done", failures = 2)))

        assertEquals(TaskbarProgress(TaskbarState.ERROR, 100), taskbarProgress(entries))
    }

    @Test
    fun `only the latest finished job decides, so a clean one clears an old error`() {
        val entries = listOf(finished(JobOutcome("old", failures = 2)), finished(JobOutcome("new")))

        assertEquals(TaskbarProgress(TaskbarState.NONE, 0), taskbarProgress(entries))
    }

    @Test
    fun `a job the user stopped is not an error`() {
        assertEquals(
            TaskbarProgress(TaskbarState.NONE, 0),
            taskbarProgress(listOf(finished(JobOutcome("stopped", stopped = true)))),
        )
    }

    @Test
    fun `a running job wins over an earlier error`() {
        val entries = listOf(finished(JobOutcome("old", failures = 1)), entry(JobState.Running(JobProgress(1, 4, "a"))))

        assertEquals(TaskbarProgress(TaskbarState.NORMAL, 25), taskbarProgress(entries))
    }
}
