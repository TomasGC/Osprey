package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class JobTextTest {

    private fun entry(state: JobState) = JobEntry(1L, "Reorg", state)

    @Test
    fun `progress text counts the file being started`() {
        assertEquals("1 of 10: a.jpg", progressText(JobProgress(0, 10, "a.jpg")))
        assertEquals("10 of 10: z.jpg", progressText(JobProgress(9, 10, "z.jpg")))
    }

    @Test
    fun `a title is the job name, with how many jobs wait behind it`() {
        assertEquals("Reorg", jobTitle(entry(JobState.Running()), waiting = 0))
        assertEquals("Reorg (+2 waiting)", jobTitle(entry(JobState.Running()), waiting = 2))
    }

    @Test
    fun `a waiting job says it is waiting`() {
        assertEquals("Waiting for the jobs before it", jobBody(JobState.Queued))
    }

    @Test
    fun `a running job with no file reported yet says it is working`() {
        assertEquals("Working...", jobBody(JobState.Running()))
    }

    @Test
    fun `a running job shows its file`() {
        assertEquals("3 of 8: a.jpg", jobBody(JobState.Running(JobProgress(2, 8, "a.jpg"))))
    }

    @Test
    fun `a stopping job says so, whatever file it is on`() {
        val expected = "Stopping after this file..."

        assertEquals(expected, jobBody(JobState.Running(stopping = true)))
        assertEquals(expected, jobBody(JobState.Running(JobProgress(2, 8, "a.jpg"), stopping = true)))
    }

    @Test
    fun `a finished job shows its summary`() {
        assertEquals("Reorg: 5 done", jobBody(JobState.Finished(JobOutcome("Reorg: 5 done"))))
    }

    @Test
    fun `a summary of a stopped job says it was stopped`() {
        assertEquals("Reorg: 5 done, 0 failed (stopped)", withStoppedNote("Reorg: 5 done, 0 failed", stopped = true))
    }

    @Test
    fun `a summary of a job that ran to the end is left as it is`() {
        assertEquals("Reorg: 5 done, 0 failed", withStoppedNote("Reorg: 5 done, 0 failed", stopped = false))
    }
}
