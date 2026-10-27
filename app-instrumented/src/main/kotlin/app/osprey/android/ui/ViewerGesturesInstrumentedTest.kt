package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.ViewTurns
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 5_000L
private const val SETTLE_MS = 1_000L
private const val EDGE_MARGIN = 3f
private const val FINGER_GAP = 40f
private const val PINCH_SPREAD = 200f
private const val DRAG = 80f
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

/**
 * The Android viewer's zoom, pan, turn and close behaviour (#51): the real [MediaPreview], with a fake reader so no
 * folder grant is needed, driven by real touch input, two-finger pinch included.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViewerGesturesInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val handle = object : FileHandle {}
    private val turns = ViewTurns()
    private var closed = 0

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer = ONE_PIXEL
    }

    private fun showViewer() {
        composeRule.setContent {
            MediaPreview(
                reader, turns, handle, "IMG_0001.jpg", checked = false, checkLabel = "Keep",
                onCheckedChange = {}, onClose = { closed++ },
            )
        }
        waitForZoomReadout("100%")
    }

    private fun waitForZoomReadout(text: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    @Test
    fun pinchingOutZoomsIn() {
        showViewer()

        composeRule.onNodeWithTag("viewer").performTouchInput {
            pinch(
                Offset(centerX - FINGER_GAP, centerY), Offset(centerX - PINCH_SPREAD, centerY),
                Offset(centerX + FINGER_GAP, centerY), Offset(centerX + PINCH_SPREAD, centerY),
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("100%").assertCountEquals(0)
    }

    @Test
    fun aDoubleTapZoomsInAndASecondOneGoesBackToFit() {
        showViewer()

        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("300%")

        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("100%")
    }

    @Test
    fun draggingAZoomedPhotoNeitherClosesTheViewerNorChangesTheZoom() {
        showViewer()
        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("300%")

        composeRule.onNodeWithTag("viewer").performTouchInput { swipe(center, Offset(centerX - DRAG, centerY - DRAG)) }
        settle()

        composeRule.onNodeWithText("300%").assertExists()
        assertEquals(0, closed)
    }

    @Test
    fun turningRightAndLeftIsRememberedForThePhoto() {
        showViewer()

        composeRule.onNodeWithContentDescription("Rotate right").performClick()
        composeRule.waitForIdle()
        assertEquals(1, turns.turnsOf(handle))

        composeRule.onNodeWithContentDescription("Rotate left").performClick()
        composeRule.onNodeWithContentDescription("Rotate left").performClick()
        composeRule.waitForIdle()
        assertEquals(3, turns.turnsOf(handle))
    }

    @Test
    fun turningAZoomedPhotoGoesBackToFit() {
        showViewer()
        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("300%")

        composeRule.onNodeWithContentDescription("Rotate right").performClick()

        waitForZoomReadout("100%")
    }

    @Test
    fun theViewerStartsFromTheTurnThePhotoAlreadyHas() {
        turns.turn(handle, 1)
        showViewer()

        composeRule.onNodeWithContentDescription("Rotate right").performClick()
        composeRule.waitForIdle()

        assertEquals(2, turns.turnsOf(handle))
    }

    @Test
    fun aTapBesideThePhotoClosesTheViewer() {
        showViewer()

        // The photo is square and fills the shorter side, so the empty space is along the longer one.
        composeRule.onNodeWithTag("viewer").performTouchInput {
            click(if (height > width) Offset(centerX, EDGE_MARGIN) else Offset(EDGE_MARGIN, centerY))
        }
        settle()

        assertEquals(1, closed)
    }

    @Test
    fun aTapOnThePhotoLeavesTheViewerOpen() {
        showViewer()

        composeRule.onNodeWithTag("viewer").performTouchInput { click(center) }
        settle()

        assertEquals(0, closed)
    }
}
