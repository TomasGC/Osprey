package app.osprey.android.jobs

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

private const val WAIT_MS = 15_000L
private const val POLL_MS = 100L
private const val NANOS_PER_MS = 1_000_000L

/** How `dumpsys activity services` names a live service: `ServiceRecord{... u12 app.osprey/.android.jobs.JobService ...}`. */
private val JOB_SERVICE_RECORD = Regex("ServiceRecord\\{[^}]*JobService")

/** The app's own notifications as the system holds them, with bounded waits on that state, for the job tests (#94). */
class NotificationProbe(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Waits for the previous test's job service to be gone (a job queued while it stops can run without a service,
     * #111), allows notifications (a refusal would drop every `notify()` without a word) and starts from an empty shade.
     */
    fun prepare() {
        waitFor("the previous job service to stop") { !jobServiceRunning() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertTrue("notifications are off for the app", manager.areNotificationsEnabled())
        manager.cancelAll()
    }

    fun clean() = manager.cancelAll()

    /** True while the system lists a job service of the app: it ends itself when the queue is empty. */
    fun jobServiceRunning(): Boolean {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys activity services ${context.packageName}")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return JOB_SERVICE_RECORD.containsMatchIn(dump)
    }

    fun count(): Int = manager.activeNotifications.size

    /** Titles of the running notifications. */
    fun ongoingTitles(): List<String?> = titles { it.isOngoing }

    /** Titles of the result notifications. */
    fun resultTitles(): List<String?> = titles { !it.isOngoing }

    /** Texts of the running notifications. */
    fun ongoingTexts(): List<String?> = manager.activeNotifications.filter { it.isOngoing }
        .map { it.notification.extras.getString(Notification.EXTRA_TEXT) }

    fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WAIT_MS * NANOS_PER_MS
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $what; the shade holds: ${snapshot()}" }
            Thread.sleep(POLL_MS)
        }
    }

    /** What the shade holds, for a failure message: id, channel, running or result, title and text of each. */
    private fun snapshot(): String = manager.activeNotifications.joinToString(" | ") {
        val kind = if (it.isOngoing) "running" else "result"
        val title = it.notification.extras.getString(Notification.EXTRA_TITLE)
        val text = it.notification.extras.getString(Notification.EXTRA_TEXT)
        "${it.id} ${it.notification.channelId} $kind '$title' '$text'"
    }

    private fun titles(keep: (StatusBarNotification) -> Boolean): List<String?> =
        manager.activeNotifications.filter(keep).map { it.notification.extras.getString(Notification.EXTRA_TITLE) }
}
