package app.osprey.android.jobs

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

private const val WAIT_MS = 15_000L
private const val POLL_MS = 100L
private const val NANOS_PER_MS = 1_000_000L

/** The app's own notifications as the system holds them, with bounded waits on that state, for the job tests (#94). */
class NotificationProbe(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Allows notifications (a refusal would drop every `notify()` without a word) and starts from an empty shade. */
    fun prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertTrue("notifications are off for the app", manager.areNotificationsEnabled())
        manager.cancelAll()
    }

    fun clean() = manager.cancelAll()

    fun count(): Int = manager.activeNotifications.size

    /** Titles of the running notifications. */
    fun ongoingTitles(): List<String?> = titles { it.isOngoing }

    /** Titles of the result notifications. */
    fun resultTitles(): List<String?> = titles { !it.isOngoing }

    fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WAIT_MS * NANOS_PER_MS
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $what" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun titles(keep: (StatusBarNotification) -> Boolean): List<String?> =
        manager.activeNotifications.filter(keep).map { it.notification.extras.getString(Notification.EXTRA_TITLE) }
}
