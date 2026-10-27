package app.osprey.desktop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.ViewTurns
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val SETTLE_MS = 1_000L
private const val EDGE_MARGIN = 10f
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

/** The viewer's zoom, pan, turn and close behaviour (#51), on a photo that is shown at fit inside the window. */
@OptIn(ExperimentalTestApi::class)
class ViewerGesturesTest {

    private val handle: FileHandle = PathHandle(Path.of("IMG_0001.jpg"))
    private val turns = ViewTurns()
    private var closed = 0

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer = ONE_PIXEL
    }

    private fun ComposeUiTest.viewer() {
        setContent {
            MediaPreview(
                reader, turns, handle, "IMG_0001.jpg", checked = false, checkLabel = "Keep",
                onCheckedChange = {}, onClose = { closed++ },
            )
        }
        waitForZoomReadout("100%")
    }

    private fun ComposeUiTest.waitForZoomReadout(text: String) {
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    @Test
    fun `the wheel zooms in at the cursor and out again`() = runComposeUiTest {
        viewer()

        onNodeWithTag("viewer").performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        waitForZoomReadout("125%")

        onNodeWithTag("viewer").performMouseInput { scroll(1f) }
        waitForZoomReadout("100%")
    }

    @Test
    fun `a double click zooms in and a second one goes back to fit`() = runComposeUiTest {
        viewer()

        onNodeWithTag("viewer").performMouseInput { doubleClick(center) }
        waitForZoomReadout("300%")

        onNodeWithTag("viewer").performMouseInput { doubleClick(center) }
        waitForZoomReadout("100%")
    }

    @Test
    fun `dragging a zoomed photo neither closes the viewer nor changes the zoom`() = runComposeUiTest {
        viewer()
        onNodeWithTag("viewer").performMouseInput { doubleClick(center) }
        waitForZoomReadout("300%")

        onNodeWithTag("viewer").performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(60f, 30f))
            release()
        }
        settle()

        onNodeWithText("300%").assertExists()
        assertEquals(0, closed)
    }

    @Test
    fun `turning right and left is remembered for the photo`() = runComposeUiTest {
        viewer()

        onNodeWithContentDescription("Rotate right").performClick()
        waitForIdle()
        assertEquals(1, turns.turnsOf(handle))

        onNodeWithContentDescription("Rotate left").performClick()
        onNodeWithContentDescription("Rotate left").performClick()
        waitForIdle()
        assertEquals(3, turns.turnsOf(handle))
    }

    @Test
    fun `turning a zoomed photo goes back to fit`() = runComposeUiTest {
        viewer()
        onNodeWithTag("viewer").performMouseInput { doubleClick(center) }
        waitForZoomReadout("300%")

        onNodeWithContentDescription("Rotate right").performClick()

        waitForZoomReadout("100%")
    }

    @Test
    fun `the viewer starts from the turn the photo already has`() = runComposeUiTest {
        turns.turn(handle, 1)
        viewer()

        onNodeWithContentDescription("Rotate right").performClick()
        waitForIdle()

        assertEquals(2, turns.turnsOf(handle))
    }

    @Test
    fun `a click beside the photo closes the viewer`() = runComposeUiTest {
        viewer()

        onNodeWithTag("viewer").performMouseInput { click(Offset(EDGE_MARGIN, center.y)) }
        settle()

        assertEquals(1, closed)
    }

    @Test
    fun `a click on the photo leaves the viewer open`() = runComposeUiTest {
        viewer()

        onNodeWithTag("viewer").performMouseInput { click(center) }
        settle()

        assertEquals(0, closed)
    }

    @Test
    fun `escape closes the viewer`() = runComposeUiTest {
        viewer()

        onNodeWithTag("viewer").performKeyInput { pressKey(Key.Escape) }
        settle()

        assertEquals(1, closed)
    }

    @Test
    fun `there is only one zoom readout`() = runComposeUiTest {
        viewer()

        onAllNodesWithText("100%").assertCountEquals(1)
    }
}
