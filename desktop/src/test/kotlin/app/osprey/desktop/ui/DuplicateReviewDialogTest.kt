package app.osprey.desktop.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.actions.reorg.DuplicateReview
import app.osprey.actions.reorg.DuplicateSide
import app.osprey.actions.reorg.DuplicateViewMode
import app.osprey.actions.reorg.ReorgDuplicate
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.media.CARD_DECODE_MAX_SIDE
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.ViewTurns
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val MANY = 300
private const val MOST_ROWS_UNDECODED = 60
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

@OptIn(ExperimentalTestApi::class)
class DuplicateReviewDialogTest : TimedTest() {

    /** Every decode asked for: the file's label and the side length requested (null = full size). */
    private val decodes = ConcurrentLinkedQueue<Pair<String, Int?>>()
    private val review = DuplicateReview()
    private var continued = 0
    private var cancelled = 0

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
            decodes += (entry as PathHandle).path.toString().replace('\\', '/') to maxSide
            return ONE_PIXEL
        }
    }

    private fun side(label: String, name: String, year: String) = DuplicateSide(
        PathHandle(Path.of(label)), label, name, 2_048, LocalDateTime.of(2018, 6, 15, 12, 0), year,
    )

    private fun pair(name: String, source: String = "Camera", year: String = "2018") =
        ReorgDuplicate(source, side("$source/$name", name, ""), side("$year/$name", name, year))

    private fun ComposeUiTest.show(vararg pairs: ReorgDuplicate) {
        review.open(pairs.toList())
        setContent {
            DuplicateReviewDialog(review, reader, ViewTurns(), { continued++ }, { cancelled++ })
        }
        waitForIdle()
    }

    private fun ComposeUiTest.click(description: String) {
        onNodeWithContentDescription(description).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.clickText(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.waitForDecodes(count: Int) {
        waitUntil(timeoutMillis = WAIT_MS) { decodes.size >= count }
        waitForIdle()
    }

    @Test
    fun `it shows the question, both headers, a row per pair and an empty Trash count`() = runComposeUiTest {
        show(pair("a.jpg"), pair("b.jpg"))

        onNodeWithText("Which files do you want to keep?").assertExists()
        onNodeWithText("Files from Camera").assertExists()
        onNodeWithText("Files already in 2018").assertExists()
        onNodeWithText("a.jpg").assertExists()
        onNodeWithText("b.jpg").assertExists()
        onNodeWithText("No file will go to the Trash").assertExists()
    }

    @Test
    fun `unticking a file updates the Trash count`() = runComposeUiTest {
        show(pair("a.jpg"))
        onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOn()

        click("Keep Camera/a.jpg")

        onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOff()
        onNodeWithText("1 file will go to the Trash").assertExists()
    }

    @Test
    fun `a header box unticks its whole column and ticks it again`() = runComposeUiTest {
        show(pair("a.jpg"), pair("b.jpg"))

        click("Keep all from the input")
        onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOff()
        onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        onNodeWithContentDescription("Keep 2018/a.jpg").assertIsOn()
        onNodeWithText("2 files will go to the Trash").assertExists()

        click("Keep all from the input")
        onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOn()
        onNodeWithText("No file will go to the Trash").assertExists()
    }

    @Test
    fun `miniatures are decoded at card size and the details view decodes nothing`() = runComposeUiTest {
        show(pair("a.jpg"))
        waitForDecodes(2)
        assertTrue(decodes.all { it.second == CARD_DECODE_MAX_SIDE })

        decodes.clear()
        clickText("Details")
        waitForIdle()

        assertTrue(decodes.isEmpty())
        onNodeWithContentDescription("Open Camera/a.jpg").assertExists()
        onNodeWithContentDescription("Open 2018/a.jpg").assertExists()
    }

    @Test
    fun `a file name in the details view opens the zoomable preview and decodes only that file`() = runComposeUiTest {
        review.setMode(DuplicateViewMode.DETAILS)
        show(pair("a.jpg"))

        click("Open Camera/a.jpg")

        onNodeWithTag("viewer").assertExists()
        waitForDecodes(1)
        assertEquals(listOf("Camera/a.jpg" to null), decodes.toList())
    }

    @Test
    fun `a miniature opens the preview, its Keep box changes that file and closing returns to the list`() =
        runComposeUiTest {
            show(pair("a.jpg"))

            click("Open 2018/a.jpg")
            onNodeWithTag("viewer").assertExists()
            onNode(isToggleable() and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
                .performClick()
            waitForIdle()
            clickText("Close")

            onNodeWithTag("viewer").assertDoesNotExist()
            onNodeWithContentDescription("Keep 2018/a.jpg").assertIsOff()
            onNodeWithText("1 file will go to the Trash").assertExists()
        }

    @Test
    fun `switching between the views keeps every tick`() = runComposeUiTest {
        show(pair("a.jpg"), pair("b.jpg"))
        click("Keep Camera/b.jpg")

        clickText("Details")
        onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOn()

        clickText("Miniatures")
        onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        onNodeWithText("1 file will go to the Trash").assertExists()
    }

    @Test
    fun `a long list is composed lazily and decodes only the rows in view`() = runComposeUiTest {
        show(*Array(MANY) { pair("img$it.jpg") })
        waitForDecodes(2)

        assertTrue("decoded ${decodes.size} of ${MANY * 2}", decodes.size < MOST_ROWS_UNDECODED)
    }

    @Test
    fun `Continue and Cancel call back`() = runComposeUiTest {
        show(pair("a.jpg"))

        clickText("Continue")
        clickText("Cancel")

        assertEquals(1, continued)
        assertEquals(1, cancelled)
    }

    @Test
    fun `nothing is drawn while the review is closed`() = runComposeUiTest {
        setContent { DuplicateReviewDialog(review, reader, ViewTurns(), {}, {}) }
        waitForIdle()

        onNodeWithText("Which files do you want to keep?").assertDoesNotExist()
    }
}
