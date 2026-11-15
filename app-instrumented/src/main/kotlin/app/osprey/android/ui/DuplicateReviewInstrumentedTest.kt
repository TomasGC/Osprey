package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.actions.reorg.DuplicateReview
import app.osprey.actions.reorg.DuplicateSide
import app.osprey.actions.reorg.DuplicateViewMode
import app.osprey.actions.reorg.ReorgDuplicate
import app.osprey.filesystem.FileHandle
import app.osprey.media.CARD_DECODE_MAX_SIDE
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.ViewTurns
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 5_000L
private const val MANY = 300
private const val MOST_ROWS_UNDECODED = 60
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

/**
 * The Android "Which files do you want to keep?" dialog (#91): the real [DuplicateReviewDialog] over a real
 * [DuplicateReview], with a counting fake reader so no folder grant is needed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DuplicateReviewInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Every decode asked for: the file's label and the side length requested (null = full size). */
    private val decodes = ConcurrentLinkedQueue<Pair<String, Int?>>()
    private val labels = ConcurrentHashMap<FileHandle, String>()
    private val review = DuplicateReview()
    private var continued = 0
    private var cancelled = 0

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer {
            decodes += labels.getValue(entry) to maxSide
            return ONE_PIXEL
        }
    }

    private fun side(label: String, name: String, year: String): DuplicateSide {
        val handle = object : FileHandle {}
        labels[handle] = label
        return DuplicateSide(handle, label, name, 2_048, LocalDateTime.of(2018, 6, 15, 12, 0), year)
    }

    private fun pair(name: String, source: String = "Camera", year: String = "2018") =
        ReorgDuplicate(source, side("$source/$name", name, ""), side("$year/$name", name, year))

    private fun show(vararg pairs: ReorgDuplicate) {
        review.open(pairs.toList())
        composeRule.setContent {
            DuplicateReviewDialog(review, reader, ViewTurns(), { continued++ }, { cancelled++ })
        }
        composeRule.waitForIdle()
    }

    private fun click(description: String) {
        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.waitForIdle()
    }

    private fun clickText(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun waitForDecodes(count: Int) {
        composeRule.waitUntil(timeoutMillis = WAIT_MS) { decodes.size >= count }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun showsTheQuestionBothHeadersARowPerPairAndAnEmptyTrashCount() {
        show(pair("a.jpg"), pair("b.jpg"))

        composeRule.onNodeWithText("Which files do you want to keep?").assertExists()
        composeRule.onNodeWithText("Files from Camera").assertExists()
        composeRule.onNodeWithText("Files already in 2018").assertExists()
        composeRule.onNodeWithText("a.jpg").assertExists()
        composeRule.onNodeWithText("b.jpg").assertExists()
        composeRule.onNodeWithText("No file will go to the Trash").assertExists()
    }

    @Test
    fun unTickingAFileUpdatesTheTrashCount() {
        show(pair("a.jpg"))
        composeRule.onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOn()

        click("Keep Camera/a.jpg")

        composeRule.onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOff()
        composeRule.onNodeWithText("1 file will go to the Trash").assertExists()
    }

    @Test
    fun aHeaderBoxUnTicksItsWholeColumnAndTicksItAgain() {
        show(pair("a.jpg"), pair("b.jpg"))

        click("Keep all from the input")
        composeRule.onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOff()
        composeRule.onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        composeRule.onNodeWithContentDescription("Keep 2018/a.jpg").assertIsOn()
        composeRule.onNodeWithText("2 files will go to the Trash").assertExists()

        click("Keep all from the input")
        composeRule.onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOn()
        composeRule.onNodeWithText("No file will go to the Trash").assertExists()
    }

    @Test
    fun miniaturesAreDecodedAtCardSizeAndTheDetailsViewDecodesNothing() {
        show(pair("a.jpg"))
        waitForDecodes(2)
        assertTrue(decodes.all { it.second == CARD_DECODE_MAX_SIDE })

        decodes.clear()
        clickText("Details")

        assertTrue(decodes.isEmpty())
        composeRule.onNodeWithContentDescription("Open Camera/a.jpg").assertExists()
        composeRule.onNodeWithContentDescription("Open 2018/a.jpg").assertExists()
    }

    @Test
    fun aFileNameInTheDetailsViewOpensThePreviewAndDecodesOnlyThatFile() {
        review.setMode(DuplicateViewMode.DETAILS)
        show(pair("a.jpg"))

        click("Open Camera/a.jpg")

        composeRule.onNodeWithTag("viewer").assertExists()
        waitForDecodes(1)
        // The Android viewer decodes a bounded picture first (#60), so only the file and the size are checked.
        assertEquals(setOf("Camera/a.jpg"), decodes.map { it.first }.toSet())
        assertTrue(decodes.none { it.second == CARD_DECODE_MAX_SIDE })
    }

    @Test
    fun aMiniatureOpensThePreviewItsKeepBoxChangesThatFileAndClosingReturnsToTheList() {
        show(pair("a.jpg"))

        click("Open 2018/a.jpg")
        composeRule.onNodeWithTag("viewer").assertExists()
        composeRule.onNode(isToggleable() and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .performClick()
        composeRule.waitForIdle()
        clickText("Close")

        composeRule.onNodeWithTag("viewer").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Keep 2018/a.jpg").assertIsOff()
        composeRule.onNodeWithText("1 file will go to the Trash").assertExists()
    }

    @Test
    fun switchingBetweenTheViewsKeepsEveryTick() {
        show(pair("a.jpg"), pair("b.jpg"))
        click("Keep Camera/b.jpg")

        clickText("Details")
        composeRule.onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        composeRule.onNodeWithContentDescription("Keep Camera/a.jpg").assertIsOn()

        clickText("Miniatures")
        composeRule.onNodeWithContentDescription("Keep Camera/b.jpg").assertIsOff()
        composeRule.onNodeWithText("1 file will go to the Trash").assertExists()
    }

    @Test
    fun aLongListDecodesOnlyTheRowsInView() {
        show(*Array(MANY) { pair("img$it.jpg") })
        waitForDecodes(2)

        assertTrue("decoded ${decodes.size} of ${MANY * 2}", decodes.size < MOST_ROWS_UNDECODED)
    }

    @Test
    fun continueAndCancelCallBack() {
        show(pair("a.jpg"))

        clickText("Continue")
        clickText("Cancel")

        assertEquals(1, continued)
        assertEquals(1, cancelled)
    }

    @Test
    fun nothingIsDrawnWhileTheReviewIsClosed() {
        composeRule.setContent { DuplicateReviewDialog(review, reader, ViewTurns(), {}, {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Which files do you want to keep?").assertDoesNotExist()
    }

    @Test
    fun backCancelsTheReviewAndWithThePreviewOpenClosesOnlyThePreview() {
        show(pair("a.jpg"))
        click("Open Camera/a.jpg")

        pressBack()
        composeRule.onNodeWithTag("viewer").assertDoesNotExist()
        assertEquals(0, cancelled)

        pressBack()
        assertEquals(1, cancelled)
    }

    @Test
    fun bothSidesOfARowAndTheFooterButtonsFitThePhoneWidth() {
        show(pair("a.jpg"))

        composeRule.onNodeWithContentDescription("Keep Camera/a.jpg").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Keep 2018/a.jpg").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open 2018/a.jpg").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
