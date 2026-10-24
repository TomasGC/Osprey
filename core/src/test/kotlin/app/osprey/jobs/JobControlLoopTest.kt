package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobControlLoopTest {

    @Test
    fun `every item is handled in order and reported before it starts`() {
        val control = RecordingJobControl()
        val handled = mutableListOf<String>()

        val finished = control.forEachUntilStopped(listOf("a", "b", "c"), { "file $it" }) { handled += it }

        assertTrue(finished)
        assertEquals(listOf("a", "b", "c"), handled)
        assertEquals(
            listOf(JobProgress(0, 3, "file a"), JobProgress(1, 3, "file b"), JobProgress(2, 3, "file c")),
            control.reports,
        )
    }

    @Test
    fun `a stop is seen before the next item, never in the middle of one`() {
        val control = RecordingJobControl(stopAfterReports = 2)
        val handled = mutableListOf<String>()

        val finished = control.forEachUntilStopped(listOf("a", "b", "c"), { it }) { handled += it }

        assertFalse(finished)
        assertEquals(listOf("a", "b"), handled)
    }

    @Test
    fun `a stop asked before the first item handles nothing`() {
        val control = RecordingJobControl(stopAfterReports = 0)
        val handled = mutableListOf<String>()

        assertFalse(control.forEachUntilStopped(listOf("a"), { it }) { handled += it })

        assertEquals(emptyList<String>(), handled)
        assertEquals(emptyList<JobProgress>(), control.reports)
    }

    @Test
    fun `no items is a finished run that reported nothing`() {
        val control = RecordingJobControl()

        assertTrue(control.forEachUntilStopped(emptyList<String>(), { it }) { error("not called") })

        assertEquals(emptyList<JobProgress>(), control.reports)
    }
}
