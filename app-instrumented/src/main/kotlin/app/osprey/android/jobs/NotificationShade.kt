package app.osprey.android.jobs

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

private const val WAIT_MS = 10_000L
private const val MAX_LEVELS = 3

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
     * The Stop button of the notification titled [title], as it is on screen without expanding anything: the
     * nearest ancestor of the title that holds a "Stop" is the notification's own card, so another app's Stop is
     * never taken. Null when the title or its Stop is not shown.
     */
    fun stopOf(title: String): UiObject2? {
        var level: UiObject2? = device.wait(Until.findObject(By.text(title)), WAIT_MS)?.parent
        repeat(MAX_LEVELS) {
            level?.findObject(By.text(STOP_LABEL))?.let { return it }
            level = level?.parent
        }
        return null
    }
}
