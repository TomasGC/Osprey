package app.osprey.android.jobs

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.jobs.JobNotification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What clearing the results of earlier jobs does to the notifications the system holds, and what the running
 * notification's own layout shows, Stop included (#94).
 */
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

    @Test
    fun theRunningViewShowsItsTitleTextAndStop() {
        val view = inflate(JobNotification("Reorg apply", "3 of 10: a.jpg", percent = 30, stoppableJobId = 7))

        assertEquals("Reorg apply", view.text("notification_title"))
        assertEquals("3 of 10: a.jpg", view.text("notification_text"))
        assertEquals(View.VISIBLE, view.find("notification_progress").visibility)
        assertEquals(View.VISIBLE, view.find("notification_stop").visibility)
        assertEquals("Stop", view.text("notification_stop"))
    }

    @Test
    fun aJobThatCanNotBeStoppedShowsNoStop() {
        val view = inflate(JobNotification("Reorg apply", "Stopping after this file...", stoppableJobId = null))

        assertEquals(View.GONE, view.find("notification_stop").visibility)
        assertEquals(View.GONE, view.find("notification_progress").visibility)
    }

    private fun inflate(model: JobNotification): View {
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = JobNotifications(context).ongoingView(model).apply(context, FrameLayout(context))
        }
        return checkNotNull(view)
    }

    private fun View.find(name: String): View =
        findViewById(context.resources.getIdentifier(name, "id", context.packageName))

    private fun View.text(name: String): String = (find(name) as TextView).text.toString()
}
