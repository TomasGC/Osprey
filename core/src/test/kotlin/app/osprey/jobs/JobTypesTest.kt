package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobTypesTest {

    @Test
    fun `progress is the share of files handled so far`() {
        assertEquals(0.3f, JobProgress(3, 10, "a.jpg").fraction, 0.0001f)
        assertEquals(1f, JobProgress(10, 10, "a.jpg").fraction, 0.0001f)
    }

    @Test
    fun `progress with no total is zero, never a division by zero`() {
        assertEquals(0f, JobProgress(0, 0, "").fraction, 0f)
    }

    @Test
    fun `an outcome with no failure that was not stopped succeeded`() {
        assertTrue(JobOutcome("done").succeeded)
    }

    @Test
    fun `an outcome with a failure did not succeed`() {
        assertFalse(JobOutcome("done", failures = 1).succeeded)
    }

    @Test
    fun `a stopped outcome did not succeed`() {
        assertFalse(JobOutcome("done", stopped = true).succeeded)
    }

    @Test
    fun `no job control never stops and ignores progress`() {
        NoJobControl.report(1, 2, "a.jpg")

        assertFalse(NoJobControl.shouldStop())
    }
}
