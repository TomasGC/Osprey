package app.osprey.android.filesystem

import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.os.Process
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
private const val ROOTS_LIST_ID = "roots_list"
private const val ROOT_TITLE_ID = "android:id/title"
private const val IMAGE_BUTTON = "android.widget.ImageButton"

private const val FOLDER_PREFIX = "osprey_instrumented_"
private const val STORAGE_ROOT = "/sdcard"
private const val PER_USER_RANGE = 100_000

/** Why a test that stages folders through the shell is skipped in a work profile. */
internal const val SKIP_NOT_SHARED = "The shell does not see this user's storage (a work profile): nothing to stage."
private const val VOLUMES_ROOT = "/storage"
private val NOT_VOLUMES = setOf("emulated", "self")
private const val FOLDER_MATCH = "-name $FOLDER_PREFIX*"

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

    /**
     * A new throwaway folder under the granted tree, or null when there is no grant (the caller skips). The prefix
     * must not be the picker flow's: [cleanUp] removes every folder with that name, wherever it is.
     */
    fun workFolder(context: Context, prefix: String): DocumentFile? {
        require(!prefix.startsWith(FOLDER_PREFIX)) { "$prefix is the picker flow's folder name, which cleanUp removes" }
        return root(context)?.let {
            it.createDirectory("${prefix}_${System.currentTimeMillis()}")
                ?: error("could not create a throwaway test folder under the granted tree")
        }
    }

    fun root(context: Context): DocumentFile? {
        val granted = grant(context) ?: askForOne(context)
        return granted?.let { DocumentFile.fromTreeUri(context, it.uri) }?.takeIf { it.isDirectory }
    }

    private fun grant(context: Context): UriPermission? =
        context.contentResolver.persistedUriPermissions.firstOrNull { it.isReadPermission && it.isWritePermission }

    private fun askForOne(context: Context): UriPermission? =
        if (InstrumentationRegistry.getArguments().getString(PICKER_ARGUMENT) == "1") grantFromPicker(context) else null

    /**
     * Takes a new grant from the system picker, through the app's own Add folder button; fails loudly if it can't.
     * With [onVolume] the folder is made on the removable volume instead of the shared storage.
     */
    fun grantFromPicker(context: Context, onVolume: Boolean = false): UriPermission {
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
        // The picker opens where it was last left: go to the storage root, the first crumb, so the folder is made
        // there. On a volume, open the list of roots and take the volume's.
        if (onVolume) openRemovableVolume(device, picker) else click(device, By.res(picker, BREADCRUMB_ID))
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
     * Removes what the picker flow leaves behind: its grants (read from the tree id,
     * `primary:osprey_instrumented_<ms>`, or deeper when the picker was left in a folder of the flow's)
     * and its folders on the shared storage and the removable volumes, at any depth. Another grant or folder is
     * never touched, so a phone's own grant stays. Runs before each picker flow and when the run ends
     * (`OspreyInstrumentationRunner`).
     */
    fun cleanUp(context: Context) {
        val resolver = context.contentResolver
        removeFlowFolders(context, flowTrees(held(context)))
        // Removing a folder through its grant revokes the grant: what is left to release is read again.
        flowTrees(held(context)).forEach {
            resolver.releasePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (!sharesStorageWithShell) return
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // `find` removes what it finds itself: the command is split at spaces, so a path handed back to `rm` would
        // break at a space in a folder name above the flow's (`My Photos/osprey_instrumented_1`) and remove the
        // wrong ones.
        device.executeShellCommand("find ${searchRoots(device)} $FOLDER_MATCH -exec rm -rf {} +")
    }

    /**
     * Removes the folder of each of these flow grants through the grant itself, while it is still held. It works
     * wherever the app can write, a work profile included, where the shell cannot see the app's storage.
     */
    internal fun removeFlowFolders(context: Context, trees: List<Uri>) {
        trees.mapNotNull { DocumentFile.fromTreeUri(context, it) }.forEach { it.delete() }
    }

    /**
     * Whether the shell sees the storage the app writes to: only for the device's first user. In a work profile the
     * shell's `/sdcard` is the personal profile's, so the shell neither finds the app's folders nor may touch it.
     */
    internal val sharesStorageWithShell: Boolean
        get() = Process.myUid() / PER_USER_RANGE == 0

    private fun held(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions.map { it.uri }

    /** The granted trees that are the flow's: a folder it made, or one inside such a folder. Nothing else is listed. */
    internal fun flowTrees(granted: List<Uri>): List<Uri> =
        granted.filter { DocumentsContract.isTreeUri(it) && isFlowTree(DocumentsContract.getTreeDocumentId(it)) }

    /**
     * Whether a tree's document id (`primary:<path>`) is, or lies inside, a folder this flow made. Any folder on the
     * path counts, not only the first: a picker left deep makes the flow create its folder inside the last one.
     */
    internal fun isFlowTree(documentId: String): Boolean =
        documentId.substringAfter(':', "").split('/').any { it.startsWith(FOLDER_PREFIX) }

    /**
     * The full path of every folder this flow made on the shared storage and on the removable volumes, at any depth,
     * the outer ones first. The command runs without a shell, so the name pattern reaches `find` as typed.
     */
    internal fun flowFolders(device: UiDevice): List<String> =
        if (!sharesStorageWithShell) emptyList()
        else device.executeShellCommand("find ${searchRoots(device)} $FOLDER_MATCH")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Where to look, as `find` arguments: the shared storage and each removable volume. The slash follows a link. */
    private fun searchRoots(device: UiDevice): String =
        (listOf(STORAGE_ROOT) + removableVolumes(device)).joinToString(" ") { "$it/" }

    /**
     * The mount points of the removable volumes (an SD card, a USB drive), as `/storage/<id>`. The shared storage
     * (`emulated`) and the per-user link (`self`) are not volumes of their own.
     */
    internal fun removableVolumes(device: UiDevice): List<String> =
        device.executeShellCommand("ls $VOLUMES_ROOT").lines().map { it.trim() }
            .filter { it.isNotEmpty() && it !in NOT_VOLUMES }
            .map { "$VOLUMES_ROOT/$it" }

    /**
     * Opens the picker's list of roots (the toolbar's one image button) and takes the last: the shared storage comes
     * first and a volume after it. Neither is read by its label, which is the device's or the volume's own name.
     */
    private fun openRemovableVolume(device: UiDevice, picker: String) {
        click(device, By.pkg(picker).clazz(IMAGE_BUTTON))
        retrying(device, "the list of roots") {
            val roots = device.wait(Until.findObject(By.res(picker, ROOTS_LIST_ID)), WAIT_MS)
                ?: error("the picker shows no list of roots; on screen: ${onScreen(device)}")
            val titles = roots.findObjects(By.res(ROOT_TITLE_ID))
            check(titles.size > 1) { "no volume besides the shared storage in the picker: ${titles.map { it.text }}" }
            titles.last().click()
        }
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
        retrying(device, selector.toString()) {
            val node = find(device, selector) ?: error("nothing matched $selector; on screen: ${onScreen(device)}")
            node.click()
        }
    }

    private fun retrying(device: UiDevice, what: String, step: () -> Unit) {
        repeat(STALE_RETRIES) {
            try {
                step()
                return
            } catch (ignored: StaleObjectException) {
                // The screen redrew while looking for the node or before clicking it: look again.
            }
        }
        error("$what went stale $STALE_RETRIES times; on screen: ${onScreen(device)}")
    }

    private fun onScreen(device: UiDevice): List<String> =
        device.findObjects(By.text(Pattern.compile(".+"))).map { it.text }
}
