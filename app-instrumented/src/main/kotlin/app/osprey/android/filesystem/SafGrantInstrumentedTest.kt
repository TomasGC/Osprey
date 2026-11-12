package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

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
    fun cleaningUpRemovesTheGrantAndTheFolderTheFlowMade() {
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )
        val grant = SafGrant.grantFromPicker(context)

        SafGrant.cleanUp(context)

        assertTrue(
            "the grant is still held",
            context.contentResolver.persistedUriPermissions.none { it.uri == grant.uri },
        )
        val left = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("ls /sdcard").lines().filter { it.startsWith("osprey_instrumented_") }
        assertTrue("folders left: $left", left.isEmpty())
    }
}
