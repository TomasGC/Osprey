package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNotificationTest {

    private fun entry(id: Long, state: JobState, name: String = "Reorg apply") = JobEntry(id, name, state)

    @Test
    fun `no active job has no ongoing notification`() {
        assertNull(ongoingNotification(emptyList()))
        assertNull(ongoingNotification(listOf(entry(1, JobState.Finished(JobOutcome("done"))))))
    }

    @Test
    fun `a running job shows its file and how far it is, and can be stopped`() {
        val running = entry(7, JobState.Running(JobProgress(2, 10, "a.jpg")))

        val notification = checkNotNull(ongoingNotification(listOf(running)))

        assertEquals("Reorg apply", notification.title)
        assertEquals("3 of 10: a.jpg", notification.text)
        assertEquals(20, notification.percent)
        assertFalse(notification.indeterminate)
        assertEquals(7L, notification.stoppableJobId)
    }

    @Test
    fun `a running job that has not reported a file shows an unknown amount`() {
        val notification = checkNotNull(ongoingNotification(listOf(entry(1, JobState.Running()))))

        assertEquals("Working...", notification.text)
        assertNull(notification.percent)
        assertTrue(notification.indeterminate)
    }

    @Test
    fun `the title says how many jobs wait behind the running one`() {
        val entries = listOf(entry(1, JobState.Running()), entry(2, JobState.Queued), entry(3, JobState.Queued))

        assertEquals("Reorg apply (+2 waiting)", ongoingNotification(entries)?.title)
    }

    @Test
    fun `a finished job in the list is neither the current one nor counted as waiting`() {
        val entries = listOf(entry(1, JobState.Finished(JobOutcome("done"))), entry(2, JobState.Running()))

        val notification = checkNotNull(ongoingNotification(entries))

        assertEquals("Reorg apply", notification.title)
        assertEquals(2L, notification.stoppableJobId)
    }

    @Test
    fun `when only waiting jobs are left the first one is shown as waiting`() {
        val entries = listOf(entry(4, JobState.Queued, "Trash delete"), entry(5, JobState.Queued, "Dedupe scan"))

        val notification = checkNotNull(ongoingNotification(entries))

        assertEquals("Trash delete (+1 waiting)", notification.title)
        assertEquals("Waiting for the jobs before it", notification.text)
        assertTrue(notification.indeterminate)
        assertEquals(4L, notification.stoppableJobId)
    }

    @Test
    fun `a job that is stopping can not be stopped again`() {
        val notification = checkNotNull(ongoingNotification(listOf(entry(1, JobState.Running(stopping = true)))))

        assertEquals("Stopping after this file...", notification.text)
        assertNull(notification.stoppableJobId)
    }

    @Test
    fun `a finished job has a result notification with its summary`() {
        val done = entry(1, JobState.Finished(JobOutcome("Reorg: 12 done, 0 failed")))

        val notification = checkNotNull(resultNotification(done))

        assertEquals("Reorg apply", notification.title)
        assertEquals("Reorg: 12 done, 0 failed", notification.text)
        assertFalse(notification.failed)
        assertNull(notification.stoppableJobId)
        assertNull(notification.percent)
    }

    @Test
    fun `a job with failures is marked as failed`() {
        val failed = entry(1, JobState.Finished(JobOutcome("Reorg: 10 done, 2 failed", failures = 2)))

        assertTrue(checkNotNull(resultNotification(failed)).failed)
    }

    @Test
    fun `a job the user stopped is not a failure`() {
        val stopped = entry(1, JobState.Finished(JobOutcome("Reorg: 4 done, 0 failed", stopped = true)))

        assertFalse(checkNotNull(resultNotification(stopped)).failed)
    }

    @Test
    fun `a job that is not over has no result notification`() {
        assertNull(resultNotification(entry(1, JobState.Running())))
        assertNull(resultNotification(entry(1, JobState.Queued)))
    }

    @Test
    fun `the notice for the time limit says what happened and what to do`() {
        val notice = backgroundLimitNotification()

        assertEquals("Osprey stopped", notice.title)
        assertEquals(
            "Android limits how long a job can run in the background. Open Osprey and scan again to continue.",
            notice.text,
        )
        assertFalse(notice.failed)
        assertNull(notice.stoppableJobId)
    }
}
