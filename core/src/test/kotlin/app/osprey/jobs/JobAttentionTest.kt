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

    /** What the service holds when it starts late (#108): the last state it announced, not the queue as it is now. */
    @Test
    fun `a job that ended before the watcher began is announced and the ones announced earlier are not`() {
        val announced = listOf(entry(1, done), entry(2, done))
        val now = listOf(entry(1, done), entry(2, done), entry(3, done))

        assertEquals(listOf(entry(3, done)), newlyFinished(announced, now))
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

    /** The queue conflates: a job that returns at once can reach the watcher already finished (#108). */
    @Test
    fun `a job that appeared already finished is a new job`() {
        assertTrue(newJobAppeared(emptyList(), listOf(entry(1, done))))
        assertTrue(newJobAppeared(listOf(entry(1, done)), listOf(entry(1, done), entry(2, done))))
    }

    @Test
    fun `a job that appeared waiting or running is a new job`() {
        assertTrue(newJobAppeared(emptyList(), listOf(entry(1, JobState.Queued))))
    }

    @Test
    fun `jobs that were all known are not a new job whatever their state`() {
        val before = listOf(entry(1, JobState.Running()), entry(2, done))
        val after = listOf(entry(1, done), entry(2, done))

        assertFalse(newJobAppeared(before, after))
        assertFalse(newJobAppeared(emptyList(), emptyList()))
    }

    @Test
    fun `nothing changed is not a start`() {
        val same = listOf(entry(1, JobState.Running()), entry(2, done))

        assertFalse(startedNewJob(same, same))
    }
}
