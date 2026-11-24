package app.osprey.android.filesystem

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cleanup of the picker flow, without the picker (#130). A picker left deep makes every later flow create its
 * folder inside the last one, so the folders and the grants must be found at any depth, not only at the storage root.
 */
@RunWith(AndroidJUnit4::class)
class SafGrantCleanUpInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val stamp = System.currentTimeMillis()
    private val staged = StagedPaths(device)

    @Before
    fun onlyWhereTheShellSeesTheAppsStorage() {
        assumeTrue(SKIP_NOT_SHARED, SafGrant.sharesStorageWithShell)
    }

    @After
    fun removeWhatTheTestStaged() = staged.removeAll()

    @Test
    fun cleaningUpRemovesAFlowFolderAtTheRoot() {
        val folder = staged.folder("/sdcard/osprey_instrumented_$stamp")

        SafGrant.cleanUp(context)

        assertFalse("still there: $folder", staged.exists(folder))
    }

    @Test
    fun cleaningUpRemovesFlowFoldersNestedInAStaleOne() {
        val stale = staged.folder("/sdcard/Pictures/osprey_instrumented_$stamp")
        val inner = staged.folder("$stale/osprey_instrumented_${stamp + 1}/osprey_instrumented_${stamp + 2}")

        SafGrant.cleanUp(context)

        assertFalse("the stale folder is still there", staged.exists(stale))
        assertFalse("the nested folder is still there", staged.exists(inner))
    }

    @Test
    fun cleaningUpRemovesAFlowFolderInsideAnotherFolderAndLeavesThatFolder() {
        val parent = staged.folder("/sdcard/Pictures/osprey_parent_$stamp")
        val photo = staged.file("$parent/keep.txt")
        val flow = staged.folder("$parent/osprey_instrumented_$stamp")

        SafGrant.cleanUp(context)

        assertFalse("the flow folder is still there", staged.exists(flow))
        assertTrue("the other folder's file was removed", staged.exists(photo))
    }

    @Test
    fun aSpaceInAFolderAboveTheFlowsDoesNotMakeCleanUpRemoveTheWrongOnes() {
        // The shell command is split at spaces. A path handed back to `rm` would break at the space and remove
        // `osprey_space_<stamp>`, the folder named like the part before it.
        val decoy = staged.file("/sdcard/Pictures/osprey_space_$stamp/keep.txt")
        val parent = staged.folder("/sdcard/Pictures/osprey_space_$stamp with a space")
        val flow = staged.folder("$parent/osprey_instrumented_$stamp")

        SafGrant.cleanUp(context)

        assertFalse("the flow folder is still there", staged.exists(flow))
        assertTrue("the folder named like the part before the space was removed", staged.exists(decoy))
        assertTrue("the folder with the space was removed", staged.exists(parent))
    }

    @Test
    fun cleaningUpLeavesFoldersThatOnlyLookLikeTheFlowsAlone() {
        val lookalike = staged.folder("/sdcard/Pictures/my_osprey_instrumented_$stamp")
        val shorter = staged.folder("/sdcard/Pictures/osprey_instrumented-$stamp")

        SafGrant.cleanUp(context)

        assertTrue("removed: $lookalike", staged.exists(lookalike))
        assertTrue("removed: $shorter", staged.exists(shorter))
    }

    @Test
    fun theFoldersTheFlowMadeAreListedAtAnyDepth() {
        val stale = staged.folder("/sdcard/Pictures/osprey_instrumented_$stamp")
        val inner = staged.folder("$stale/osprey_instrumented_${stamp + 1}")

        val listed = SafGrant.flowFolders(device)

        assertTrue("not listed: $stale in $listed", stale in listed)
        assertTrue("not listed: $inner in $listed", inner in listed)
        assertEquals("a folder listed twice: $listed", listed.size, listed.toSet().size)
    }
}
