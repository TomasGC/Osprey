package app.osprey.android.filesystem

import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
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

// The picker's own ids, the same in Google's and AOSP's DocumentsUI; only the package prefix differs.
private const val NEW_FOLDER_ID = "option_menu_create_dir"
private const val DIALOG_TITLE_ID = "alertTitle"
private const val BREADCRUMB_ID = "breadcrumb_text"

private const val FOLDER_PREFIX = "osprey_instrumented_"
private const val STORAGE_ROOT = "/sdcard"

// Positive button of the picker's bottom bar (USE THIS FOLDER) and of its dialogs (OK, ALLOW), whatever the language.
private const val POSITIVE_BUTTON = "android:id/button1"

/**
 * The folder the SAF tests work in. A persisted read+write grant already on the device is reused (a phone's
 * one-time manual grant). With none, `-e grantViaPicker 1` makes the app ask for one: menu -> Settings -> Add folder ->
 * the system picker -> New folder -> Use this folder -> Allow, the way a person does it. With neither, null
 * (the tests skip). The picker is driven by resource ids, never by labels, so the phone's language does not matter.
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
        cleanUp(context) // a run that died leaves its folder and grant behind
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val picker = pickerPackage(device)
        val before = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        click(device, By.text("☰"))
        click(device, By.text("⚙ Settings"))
        click(device, By.text("Add folder"))
        device.wait(Until.hasObject(By.pkg(picker)), WAIT_MS)
        // The picker opens where it was last left: go to the storage root, the first crumb, so the folder is made there.
        click(device, By.res(picker, BREADCRUMB_ID))
        val name = "$FOLDER_PREFIX${System.currentTimeMillis()}"
        click(device, By.res(picker, NEW_FOLDER_ID))
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_MS).text = name
        click(device, By.res(POSITIVE_BUTTON))
        device.wait(Until.gone(By.res(picker, DIALOG_TITLE_ID)), WAIT_MS)
        click(device, By.text(name))
        click(device, By.res(POSITIVE_BUTTON))
        device.wait(Until.hasObject(By.res(picker, DIALOG_TITLE_ID)), WAIT_MS)
        click(device, By.res(POSITIVE_BUTTON))
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (newGrant(context, before) == null && System.currentTimeMillis() < deadline) Thread.sleep(GRANT_POLL_MS)
        return newGrant(context, before)
            ?: error("the picker flow ended without a grant; on screen: ${onScreen(device)}")
    }

    /**
     * Removes what the picker flow leaves behind: its grants (read from the tree id, `primary:osprey_instrumented_<ms>`)
     * and its folders at the root of the shared storage. Another grant or folder is never touched, so a phone's own
     * grant stays. Runs before each picker flow and when the run ends (`OspreyInstrumentationRunner`).
     */
    fun cleanUp(context: Context) {
        val resolver = context.contentResolver
        resolver.persistedUriPermissions
            .filter { DocumentsContract.isTreeUri(it.uri) }
            .filter { DocumentsContract.getTreeDocumentId(it.uri).substringAfter(':').startsWith(FOLDER_PREFIX) }
            .forEach {
                resolver.releasePersistableUriPermission(
                    it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        // No shell expands a glob here, so each folder is listed and removed by name.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("ls $STORAGE_ROOT").lines().map { it.trim() }
            .filter { it.startsWith(FOLDER_PREFIX) }
            .forEach { device.executeShellCommand("rm -rf $STORAGE_ROOT/$it") }
    }

    private fun newGrant(context: Context, before: Set<Uri>): UriPermission? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission && it.uri !in before }

    /**
     * The app that answers the folder picker: Google's on the Google images, AOSP's on the plain ones. Asked of the
     * shell, which sees every package: the app's own package manager hides it on newer Android (package visibility),
     * and a `<queries>` entry would put a test-only declaration in the shipped manifest.
     */
    private fun pickerPackage(device: UiDevice): String =
        device.executeShellCommand("cmd package resolve-activity --brief -a ${Intent.ACTION_OPEN_DOCUMENT_TREE}")
            .lineSequence().map { it.trim() }.firstOrNull { '/' in it }?.substringBefore('/')
            ?: error("no app answers ${Intent.ACTION_OPEN_DOCUMENT_TREE}")

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
