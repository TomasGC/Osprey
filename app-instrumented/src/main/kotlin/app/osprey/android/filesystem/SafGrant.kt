package app.osprey.android.filesystem

import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

private const val QUICK_MS = 2_000L
private const val WAIT_MS = 10_000L
private const val GRANT_POLL_MS = 250L
private const val STALE_RETRIES = 3

/**
 * The folder the SAF tests work in. A persisted read+write grant already on the device is reused (a phone's
 * one-time manual grant). With none, `-e grantViaPicker 1` makes the app ask for one: menu -> Settings -> Add folder ->
 * the system picker -> New folder -> Use this folder -> Allow, the way a person does it. With neither, null
 * (the tests skip). The picker's labels are those of the English AOSP and Google images.
 */
object SafGrant {
    const val PICKER_ARGUMENT = "grantViaPicker"
    const val SKIP_REASON = "No folder grant on this device: open the app once, Add folder, Allow " +
        "(or run with -e $PICKER_ARGUMENT 1 to get one from the picker)."

    /** A new throwaway folder under the granted tree, or null when there is no grant (the caller skips). */
    fun workFolder(context: Context, prefix: String): DocumentFile? =
        root(context)?.let {
            it.createDirectory("${prefix}_${System.currentTimeMillis()}")
                ?: error("could not create a throwaway test folder under the granted tree")
        }

    fun root(context: Context): DocumentFile? {
        val granted = grant(context) ?: askForOne(context)
        return granted?.let { DocumentFile.fromTreeUri(context, it.uri) }?.takeIf { it.isDirectory }
    }

    private fun grant(context: Context): UriPermission? =
        context.contentResolver.persistedUriPermissions.firstOrNull { it.isReadPermission && it.isWritePermission }

    private fun askForOne(context: Context): UriPermission? =
        if (InstrumentationRegistry.getArguments().getString(PICKER_ARGUMENT) == "1") grantFromPicker(context) else null

    /** Takes a new grant from the system picker, through the app's own Add folder button; fails loudly if it can't. */
    fun grantFromPicker(context: Context): UriPermission {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val picker = pickerPackage(context)
        val before = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        // The picker opens where it was last left, which would nest the new folder in the previous grant's tree.
        device.executeShellCommand("pm clear $picker")
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        click(device, By.text("☰"))
        click(device, By.text("⚙ Settings"))
        click(device, By.text("Add folder"))
        device.wait(Until.hasObject(By.pkg(picker)), WAIT_MS)
        val name = "osprey_instrumented_${System.currentTimeMillis()}"
        click(device, By.desc("New folder"))
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_MS).text = name
        click(device, By.text(Pattern.compile("OK", Pattern.CASE_INSENSITIVE)))
        click(device, By.text(name))
        click(device, By.text(Pattern.compile("USE THIS FOLDER", Pattern.CASE_INSENSITIVE)))
        click(device, By.text(Pattern.compile("ALLOW", Pattern.CASE_INSENSITIVE)))
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (newGrant(context, before) == null && System.currentTimeMillis() < deadline) Thread.sleep(GRANT_POLL_MS)
        return newGrant(context, before)
            ?: error("the picker flow ended without a grant; on screen: ${onScreen(device)}")
    }

    private fun newGrant(context: Context, before: Set<Uri>): UriPermission? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission && it.uri !in before }

    /** The app that answers the folder picker: Google's on the Google images, AOSP's on the plain ones. */
    private fun pickerPackage(context: Context): String =
        context.packageManager.resolveActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 0)!!.activityInfo.packageName

    private fun find(device: UiDevice, selector: BySelector): UiObject2? =
        device.wait(Until.findObject(selector), QUICK_MS)
            ?: device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
            ?: device.wait(Until.findObject(selector), WAIT_MS)

    private fun click(device: UiDevice, selector: BySelector) {
        repeat(STALE_RETRIES) {
            val node = find(device, selector)
                ?: error("nothing matched $selector; on screen: ${onScreen(device)}")
            try {
                node.click()
                return
            } catch (ignored: StaleObjectException) {
                // The screen redrew between finding the node and clicking it: find it again.
            }
        }
        error("$selector went stale $STALE_RETRIES times; on screen: ${onScreen(device)}")
    }

    private fun onScreen(device: UiDevice): List<String> =
        device.findObjects(By.text(Pattern.compile(".+"))).map { it.text }
}
