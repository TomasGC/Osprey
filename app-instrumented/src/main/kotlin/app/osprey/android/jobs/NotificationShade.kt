package app.osprey.android.jobs

import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

private const val WAIT_MS = 10_000L
private const val MAX_LEVELS = 3
private const val POLL_MS = 200L
private const val NANOS_PER_MS = 1_000_000L

/** A button label is drawn in capitals by the system's button style, and read back that way: "STOP". */
private val STOP_LABEL = Pattern.compile("stop", Pattern.CASE_INSENSITIVE)

/** The system notification shade, driven the way a person does: open it, look for a button, tap it (#94). */
class NotificationShade(private val device: UiDevice) {

    fun open() {
        device.wakeUp()
        check(device.openNotification()) { "The notification shade did not open" }
    }

    /** Collapses the shade, then goes home, so the next test never meets a shade that is still closing. */
    fun close() {
        device.executeShellCommand("cmd statusbar collapse")
        device.pressHome()
        device.waitForIdle()
    }

    /**
     * The Stop button of the notification titled [title], as it is on screen without expanding anything, looked for
     * until it is drawn: a stack that is still opening shows the title first. The nearest ancestor of the title that
     * holds a "Stop" is the notification's own card, so another app's Stop is never taken. Null when the title or its
     * Stop is not shown in time.
     */
    fun stopOf(title: String): UiObject2? {
        val deadline = System.nanoTime() + WAIT_MS * NANOS_PER_MS
        do {
            try {
                findStop(title)?.let { return it }
            } catch (ignored: StaleObjectException) {
                // The shade redrew while looking: look again.
            }
            Thread.sleep(POLL_MS)
        } while (System.nanoTime() < deadline)
        return null
    }

    /** Every text on screen, for a failure message. */
    fun texts(): List<String> = device.findObjects(By.text(Pattern.compile(".+"))).map { it.text }

    private fun findStop(title: String): UiObject2? {
        var level: UiObject2? = device.findObject(By.text(title))?.parent
        repeat(MAX_LEVELS) {
            level?.findObject(By.text(STOP_LABEL))?.let { return it }
            level = level?.parent
        }
        return null
    }
}
