package app.osprey.android.filesystem

import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafGrantInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theFolderPickerGivesTheAppANewWritableGrant() {
        // Only with the flag: the picker flow adds a grant and a folder to the device, a phone's own are left alone.
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )

        val grant = SafGrant.grantFromPicker(context)

        val folder = DocumentFile.fromTreeUri(context, grant.uri)
        assertTrue("the granted tree is not a folder", folder != null && folder.isDirectory)
        assertTrue("the granted tree is not writable", folder!!.canWrite())
    }

    @Test
    fun theFolderOfAFlowGrantIsRemovedThroughTheGrantItself() {
        // Not through the shell: on a work profile the shell sees another user's storage, not the app's.
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )
        val grant = SafGrant.grantFromPicker(context)
        val folder = DocumentFile.fromTreeUri(context, grant.uri)!!
        assertTrue("the flow's folder is not there to begin with", folder.exists())

        SafGrant.removeFlowFolders(context, listOf(grant.uri))

        assertFalse("the flow's folder is still there, the grant is still held", folder.exists())
    }

    @Test
    fun cleaningUpStillReleasesAGrantWhoseFolderIsAlreadyGone() {
        // A run that died, or an earlier cleanup, can leave a grant on a folder that no longer exists.
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )
        assumeTrue(SKIP_NOT_SHARED, SafGrant.sharesStorageWithShell)
        val grant = SafGrant.grantFromPicker(context)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Behind the grant's back: the provider is not told, so the grant stays held with nothing under it.
        device.executeShellCommand("find ${SafGrant.flowFolders(device).joinToString(" ")} -prune -exec rm -rf {} +")
        assertTrue("the folder is still there", SafGrant.flowFolders(device).isEmpty())

        SafGrant.cleanUp(context)

        val held = context.contentResolver.persistedUriPermissions.map { it.uri }
        assertTrue("the grant is still held: $held", grant.uri !in held)
    }

    @Test
    fun cleaningUpRemovesTheGrantAndTheFolderTheFlowMade() {
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )
        val grant = SafGrant.grantFromPicker(context)
        // The app's own view of its storage, which is the right user's in a work profile where the shell's is not.
        val folder = File(
            Environment.getExternalStorageDirectory(),
            DocumentsContract.getTreeDocumentId(grant.uri).substringAfter(':'),
        )
        assertTrue("the app does not see the folder the flow made: $folder", folder.exists())

        SafGrant.cleanUp(context)

        val held = context.contentResolver.persistedUriPermissions.map { it.uri }
        assertTrue("the grant is still held: $held", grant.uri !in held)
        assertFalse("the folder is still there: $folder", folder.exists())
        val left = SafGrant.flowFolders(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
        assertTrue("folders left: $left", left.isEmpty())
    }
}
