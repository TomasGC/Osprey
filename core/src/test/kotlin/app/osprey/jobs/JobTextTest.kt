package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `a name is followed by the scope it ran on, when there is one`() {
        assertEquals("Dates 2021", scopedName("Dates", "2021"))
        assertEquals("Dates", scopedName("Dates", null))
        assertEquals("Dates", scopedName("Dates", " "))
    }

    @Test
    fun `an apply summary names what was done and what failed`() {
        assertEquals("Dates apply: 3 done", applyOutcome("Dates", 3, 0, false).summary)
        assertEquals("Dates apply: 3 done, 2 failed", applyOutcome("Dates", 3, 2, false).summary)
    }

    @Test
    fun `an apply that stopped says so after its counts`() {
        assertEquals("Dates apply: 3 done, 1 failed (stopped)", applyOutcome("Dates", 3, 1, true).summary)
    }

    @Test
    fun `an apply outcome carries the failures and the stop`() {
        val hurt = applyOutcome("Dates", 3, 2, true)
        val clean = applyOutcome("Dates", 3, 0, false)

        assertEquals(2, hurt.failures)
        assertTrue(hurt.stopped)
        assertEquals(0, clean.failures)
        assertFalse(clean.stopped)
    }

    @Test
    fun `the suffix after an apply counts what was done, what failed and a stop`() {
        assertEquals("  -- APPLIED (5)", appliedSuffix(5, 0, false))
        assertEquals("  -- APPLIED (5), failed 2", appliedSuffix(5, 2, false))
        assertEquals("  -- APPLIED (5) -- STOPPED", appliedSuffix(5, 0, true))
        assertEquals("  -- APPLIED (5), failed 2 -- STOPPED", appliedSuffix(5, 2, true))
    }
}
