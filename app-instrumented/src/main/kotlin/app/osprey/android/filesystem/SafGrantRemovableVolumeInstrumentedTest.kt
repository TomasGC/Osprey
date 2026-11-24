package app.osprey.android.filesystem

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

private const val VOLUME_WAIT_MS = 60_000L
private const val VOLUME_POLL_MS = 500L

/**
 * The cleanup on a removable volume (an SD card), which the folders of the picker flow can also land on (#130).
 * The tests need such a volume. A phone may have none and cannot be given one: its volumes are device-wide, and the
 * personal profile would see them. An emulator can make a virtual one, so there the class makes it and takes it away
 * again. Anywhere else the tests are skipped, not failed.
 */
@RunWith(AndroidJUnit4::class)
class SafGrantRemovableVolumeInstrumentedTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val stamp = System.currentTimeMillis()
    private val staged = StagedPaths(device)

    @Before
    fun onlyWhereTheShellSeesTheAppsStorage() {
        assumeTrue(SKIP_NOT_SHARED, SafGrant.sharesStorageWithShell)
    }

    @After
    fun removeWhatTheTestStaged() = staged.removeAll()

    @Test
    fun cleaningUpRemovesAFlowFolderAtTheRootOfTheVolume() {
        val volume = volumeOrSkip()
        val folder = staged.folder("$volume/osprey_instrumented_$stamp")

        SafGrant.cleanUp(context)

        assertFalse("still there: $folder", staged.exists(folder))
    }

    @Test
    fun cleaningUpRemovesFlowFoldersNestedInAStaleOneOnTheVolume() {
        val volume = volumeOrSkip()
        val stale = staged.folder("$volume/Pictures/osprey_instrumented_$stamp")
        val inner = staged.folder("$stale/osprey_instrumented_${stamp + 1}/osprey_instrumented_${stamp + 2}")

        SafGrant.cleanUp(context)

        assertFalse("the stale folder is still there", staged.exists(stale))
        assertFalse("the nested folder is still there", staged.exists(inner))
    }

    @Test
    fun cleaningUpLeavesTheRestOfTheVolumeAlone() {
        val volume = volumeOrSkip()
        val photo = staged.file("$volume/Pictures/keep_$stamp.txt")
        val lookalike = staged.folder("$volume/Pictures/my_osprey_instrumented_$stamp")
        val flow = staged.folder("$volume/Pictures/osprey_instrumented_$stamp")

        SafGrant.cleanUp(context)

        assertFalse("the flow folder is still there", staged.exists(flow))
        assertTrue("removed: $photo", staged.exists(photo))
        assertTrue("removed: $lookalike", staged.exists(lookalike))
    }

    @Test
    fun theFoldersTheFlowMadeAreListedOnTheVolumeToo() {
        val volume = volumeOrSkip()
        val folder = staged.folder("$volume/Pictures/osprey_instrumented_$stamp")

        val listed = SafGrant.flowFolders(device)

        assertTrue("not listed: $folder in $listed", folder in listed)
    }

    @Test
    fun aGrantTakenOnTheVolumeThroughThePickerIsReleasedAndItsFolderRemoved() {
        val volume = volumeOrSkip()
        assumeTrue(
            "Pass -e ${SafGrant.PICKER_ARGUMENT} 1 to run this: it takes a new grant from the system picker.",
            InstrumentationRegistry.getArguments().getString(SafGrant.PICKER_ARGUMENT) == "1",
        )

        val grant = SafGrant.grantFromPicker(context, onVolume = true)

        val treeId = DocumentsContract.getTreeDocumentId(grant.uri)
        assertTrue("the grant is not on the volume: $treeId", treeId.startsWith("${volume.substringAfterLast('/')}:"))
        val made = SafGrant.flowFolders(device)
        assertTrue("no folder on the volume: $made", made.any { it.startsWith("$volume/") })

        SafGrant.cleanUp(context)

        val held = context.contentResolver.persistedUriPermissions.map { it.uri }
        assertTrue("the grant is still held: $held", grant.uri !in held)
        assertTrue("folders left: ${SafGrant.flowFolders(device)}", SafGrant.flowFolders(device).isEmpty())
    }

    @Test
    fun theSharedStorageAndTheUserLinkAreNotRemovableVolumes() {
        val volume = volumeOrSkip()

        val volumes = SafGrant.removableVolumes(device)

        assertTrue("the volume is missing: $volumes", volume in volumes)
        assertTrue("not volumes: $volumes", volumes.none { it.endsWith("/emulated") || it.endsWith("/self") })
    }

    private fun volumeOrSkip(): String {
        assumeTrue("No removable volume, and this device cannot be given one (only an emulator can).", volume != null)
        return volume!!
    }

    companion object {
        private var volume: String? = null
        private var madeTheDisk = false

        @BeforeClass
        @JvmStatic
        fun findOrMakeAVolume() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            volume = SafGrant.removableVolumes(device).firstOrNull() ?: makeVirtualVolume(device)
        }

        @AfterClass
        @JvmStatic
        fun removeTheVirtualDisk() {
            if (madeTheDisk) {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand("sm set-virtual-disk false")
            }
        }

        /** Only on an emulator (`ro.kernel.qemu`): the disk is device-wide. Null when it does not come up in time. */
        private fun makeVirtualVolume(device: UiDevice): String? {
            if (device.executeShellCommand("getprop ro.kernel.qemu").trim() != "1") return null
            device.executeShellCommand("sm set-virtual-disk true")
            madeTheDisk = true
            val disk = waitFor { device.executeShellCommand("sm list-disks").lines().firstOrNull { it.isNotBlank() } }
                ?: return null
            device.executeShellCommand("sm partition ${disk.trim()} public")
            return waitFor { SafGrant.removableVolumes(device).firstOrNull() }
        }

        private fun <T : Any> waitFor(read: () -> T?): T? {
            val deadline = System.currentTimeMillis() + VOLUME_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                read()?.let { return it }
                Thread.sleep(VOLUME_POLL_MS)
            }
            return null
        }
    }
}
