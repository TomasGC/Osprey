package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class JobAttentionTest {

    private val done = JobState.Finished(JobOutcome("done"))

    private fun entry(id: Long, state: JobState) = JobEntry(id, "Job $id", state)

    @Test
    fun `a job that ended since the last look is newly finished`() {
        val before = listOf(entry(1, JobState.Running()))
        val after = listOf(entry(1, done))

        assertEquals(listOf(entry(1, done)), newlyFinished(before, after))
    }

    @Test
    fun `a job that was already finished is not new`() {
        val both = listOf(entry(1, done))

        assertEquals(emptyList<JobEntry>(), newlyFinished(both, both))
    }

    @Test
    fun `a job that appeared already finished is new`() {
        assertEquals(listOf(entry(2, done)), newlyFinished(listOf(entry(1, done)), listOf(entry(1, done), entry(2, done))))
    }

    @Test
    fun `jobs still waiting or running are not finished`() {
        val after = listOf(entry(1, JobState.Queued), entry(2, JobState.Running()))

        assertEquals(emptyList<JobEntry>(), newlyFinished(emptyList(), after))
    }
}
