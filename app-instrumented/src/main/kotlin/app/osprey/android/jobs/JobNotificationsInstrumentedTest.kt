package app.osprey.android.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.jobs.JobNotification
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What clearing the results of earlier jobs does to the notifications the system holds (#94). */
@RunWith(AndroidJUnit4::class)
class JobNotificationsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = NotificationProbe(context)

    @Before
    fun setUp() = probe.prepare()

    @After
    fun tearDown() = probe.clean()

    @Test
    fun clearingRemovesTheResultsAndLeavesTheRunningNotification() {
        val notifications = JobNotifications(context).also { it.createChannels() }
        notifications.showResult(1, JobNotification("Result A", "A done"))
        notifications.showResult(2, JobNotification("Result B", "B done"))
        notifications.showOngoing(JobNotification("Running C", "Working...", indeterminate = true))
        probe.waitFor("three notifications") { probe.count() == 3 }

        notifications.clearResults()

        probe.waitFor("only the running one") {
            probe.resultTitles().isEmpty() && probe.ongoingTitles() == listOf("Running C")
        }
    }
}
