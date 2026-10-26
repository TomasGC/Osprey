package app.osprey.jobs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseRiskTest {

    private var nextId = 1L

    private fun entry(state: JobState, writes: Boolean = true) = JobEntry(nextId++, "Job", state, writes)

    @Test
    fun `no job is no risk`() {
        assertFalse(closeRisk(emptyList()))
    }

    @Test
    fun `a running apply is a risk`() {
        assertTrue(closeRisk(listOf(entry(JobState.Running()))))
    }

    @Test
    fun `a queued apply is a risk`() {
        assertTrue(closeRisk(listOf(entry(JobState.Queued))))
    }

    @Test
    fun `a scan changes nothing on disk so it is no risk`() {
        val scans = listOf(entry(JobState.Running(), writes = false), entry(JobState.Queued, writes = false))

        assertFalse(closeRisk(scans))
    }

    @Test
    fun `a finished apply is no risk`() {
        assertFalse(closeRisk(listOf(entry(JobState.Finished(JobOutcome("Reorg apply: 3 done"))))))
    }

    @Test
    fun `one writing job among scans and finished jobs is enough`() {
        val entries = listOf(
            entry(JobState.Finished(JobOutcome("done"))),
            entry(JobState.Running(), writes = false),
            entry(JobState.Queued),
        )

        assertTrue(closeRisk(entries))
    }
}
