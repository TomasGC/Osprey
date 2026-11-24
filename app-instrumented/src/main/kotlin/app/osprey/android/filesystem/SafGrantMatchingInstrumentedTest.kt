package app.osprey.android.filesystem

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the cleanup takes for the flow's: the tree ids, the grants and the names. No shell and no storage, so these
 * run on any device, a work profile included (#130).
 */
@RunWith(AndroidJUnit4::class)
class SafGrantMatchingInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aTreeIsTheFlowsWhenAnyFolderOnItsPathCarriesTheFlowsName() {
        assertTrue(SafGrant.isFlowTree("primary:osprey_instrumented_1"))
        assertTrue(SafGrant.isFlowTree("primary:osprey_instrumented_1/osprey_instrumented_2"))
        assertTrue(SafGrant.isFlowTree("primary:Pictures/WhatsApp/osprey_instrumented_1/osprey_instrumented_2"))
        assertTrue(SafGrant.isFlowTree("1234-ABCD:Pictures/osprey_instrumented_1"))
    }

    @Test
    fun aTreeThatIsNotTheFlowsIsNeverMatched() {
        assertFalse(SafGrant.isFlowTree("primary:DCIM"))
        assertFalse(SafGrant.isFlowTree("primary:Pictures"))
        assertFalse(SafGrant.isFlowTree("primary:"))
        assertFalse(SafGrant.isFlowTree("primary"))
        // The name must start a path segment, and carry the whole prefix.
        assertFalse(SafGrant.isFlowTree("primary:Pictures/my_osprey_instrumented_1"))
        assertFalse(SafGrant.isFlowTree("primary:Pictures/osprey_instrumented"))
        // Text before the colon is the volume, not a folder.
        assertFalse(SafGrant.isFlowTree("osprey_instrumented_1:DCIM"))
    }

    @Test
    fun onlyTheFlowsTreesAreChosenForRelease() {
        val authority = "com.android.externalstorage.documents"
        val flow = DocumentsContract.buildTreeDocumentUri(authority, "primary:osprey_instrumented_1")
        val nested = DocumentsContract.buildTreeDocumentUri(authority, "primary:Pictures/osprey_instrumented_1/x")
        val own = listOf("primary:DCIM", "primary:Pictures", "primary:Pictures/WhatsApp", "1234-ABCD:Music")
            .map { DocumentsContract.buildTreeDocumentUri(authority, it) }
        // A single document of the flow's folder is not a tree: it is not a grant the flow took.
        val document = DocumentsContract.buildDocumentUri(authority, "primary:osprey_instrumented_1")
        val other = Uri.parse("content://media/external/images/media/1")

        val chosen = SafGrant.flowTrees(own + listOf(flow, document, other, nested))

        assertEquals(listOf(flow, nested), chosen)
    }

    @Test
    fun aWorkFolderCannotTakeTheNameCleanUpRemoves() {
        // Refused before any grant is looked for, so this needs no picker and no grant.
        val refused = runCatching { SafGrant.workFolder(context, "osprey_instrumented_test") }.exceptionOrNull()

        assertTrue("accepted: $refused", refused is IllegalArgumentException)
    }
}
