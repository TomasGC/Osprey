package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `a job that is waiting or running and was not there before is a new start`() {
        assertTrue(startedNewJob(emptyList(), listOf(entry(1, JobState.Queued))))
        assertTrue(startedNewJob(listOf(entry(1, done)), listOf(entry(1, done), entry(2, JobState.Running()))))
    }

    @Test
    fun `a job that was already known is not a new start when it begins to run`() {
        val before = listOf(entry(1, JobState.Queued))
        val after = listOf(entry(1, JobState.Running()))

        assertFalse(startedNewJob(before, after))
    }

    @Test
    fun `a job that appeared already finished is not a start`() {
        assertFalse(startedNewJob(emptyList(), listOf(entry(1, done))))
    }

    @Test
    fun `nothing changed is not a start`() {
        val same = listOf(entry(1, JobState.Running()), entry(2, done))

        assertFalse(startedNewJob(same, same))
    }
}
