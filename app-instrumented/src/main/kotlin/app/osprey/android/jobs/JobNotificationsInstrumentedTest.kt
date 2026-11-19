package app.osprey.android.jobs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.jobs.JobNotification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val manager = context.getSystemService(NotificationManager::class.java)

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

    @Test
    fun theRunningNotificationSortsAheadOfAResultOfTheSameImportance() {
        val notifications = JobNotifications(context).also { it.createChannels() }
        notifications.showResult(1, JobNotification("Result A", "A done"))
        notifications.showOngoing(JobNotification("Running C", "Working...", indeterminate = true))
        probe.waitFor("both notifications") { probe.count() == 2 }

        val posted = manager.activeNotifications
        val running = posted.single { it.isOngoing }.notification
        val result = posted.single { !it.isOngoing }.notification

        assertEquals(importanceOf(result), importanceOf(running))
        val runningKey = running.sortKey
        val resultKey = result.sortKey
        assertTrue("sort keys: running=$runningKey result=$resultKey", runningKey != null && resultKey != null)
        assertTrue("running $runningKey must sort before result $resultKey", runningKey!! < resultKey!!)
    }

    @Test
    fun theRunningChannelIsSilentAndTheOldRunningChannelIsGone() {
        manager.createNotificationChannel(
            NotificationChannel("osprey_jobs", "Running jobs", NotificationManager.IMPORTANCE_LOW),
        )
        val notifications = JobNotifications(context).also { it.createChannels() }
        notifications.showOngoing(JobNotification("Running C", "Working...", indeterminate = true))
        probe.waitFor("the running notification") { probe.ongoingTitles() == listOf("Running C") }

        val channelId = manager.activeNotifications.single().notification.channelId
        val channel = checkNotNull(manager.getNotificationChannel(channelId))

        assertNull(manager.getNotificationChannel("osprey_jobs"))
        assertNull("no sound", channel.sound)
        assertFalse("no vibration", channel.shouldVibrate())
    }

    @Test
    fun aRunningChannelTheUserSwitchedOffStaysSwitchedOffUnderItsNewId() {
        val stamp = System.nanoTime()
        val legacyId = "legacy_running_$stamp"
        val newId = "running_$stamp"
        manager.createNotificationChannel(
            NotificationChannel(legacyId, "Running jobs", NotificationManager.IMPORTANCE_NONE),
        )

        JobNotifications(context, newId, legacyId).createChannels()

        val created = checkNotNull(manager.getNotificationChannel(newId))
        assertEquals(NotificationManager.IMPORTANCE_NONE, created.importance)
        assertNull(manager.getNotificationChannel(legacyId))
        manager.deleteNotificationChannel(newId)
    }

    private fun importanceOf(notification: Notification): Int =
        checkNotNull(manager.getNotificationChannel(notification.channelId)).importance

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
