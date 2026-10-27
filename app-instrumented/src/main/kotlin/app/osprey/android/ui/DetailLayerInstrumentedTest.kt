package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PhotoRegion
import app.osprey.media.PhotoSize
import app.osprey.media.PixelBuffer
import app.osprey.viewer.ViewTurns
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 10_000L
private const val SETTLE_MS = 2_000L
private const val PHOTO_WIDTH = 4100
private const val PHOTO_HEIGHT = 3075
private const val BASE_WIDTH = 164
private const val BASE_HEIGHT = 123
private const val SWIPE_FRACTION = 0.4f
private const val TOLERANCE = 8
private const val BASE_COLOR = 0xFF0000FF.toInt()
private const val TILE_COLOR = 0xFFFF0000.toInt()
private const val BYTE_MASK = 0xFF
private val CHANNEL_SHIFTS = listOf(0, 8, 16)

/**
 * The zoomed detail of the Android viewer (#60): the real [MediaPreview] with a fake reader for a 4100 x 3075 photo
 * whose base picture is solid blue and whose every region is solid red, so the centre pixel of the viewer says which
 * layer is on screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DetailLayerInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val handle = object : FileHandle {}
    private val requests = CopyOnWriteArrayList<Pair<PhotoRegion, Int>>()

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?) =
            PixelBuffer(BASE_WIDTH, BASE_HEIGHT, IntArray(BASE_WIDTH * BASE_HEIGHT) { BASE_COLOR })

        override fun imageSize(entry: FileHandle) = PhotoSize(PHOTO_WIDTH, PHOTO_HEIGHT)

        override fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer {
            requests += region to sampleSize
            val width = (region.width + sampleSize - 1) / sampleSize
            val height = (region.height + sampleSize - 1) / sampleSize
            return PixelBuffer(width, height, IntArray(width * height) { TILE_COLOR })
        }
    }

    private fun showViewer() {
        composeRule.setContent {
            MediaPreview(
                reader, ViewTurns(), handle, "IMG_0001.jpg", checked = false, checkLabel = "Keep",
                onCheckedChange = {}, onClose = {},
            )
        }
        waitForZoomReadout("100%")
        waitForCentre(BASE_COLOR)
    }

    private fun waitForZoomReadout(text: String) =
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private fun centrePixel(): Int {
        val image = composeRule.onNodeWithTag("viewer").captureToImage().asAndroidBitmap()
        return image.getPixel(image.width / 2, image.height / 2)
    }

    private fun isNear(expected: Int, actual: Int) = CHANNEL_SHIFTS.all { shift ->
        abs(((expected shr shift) and BYTE_MASK) - ((actual shr shift) and BYTE_MASK)) <= TOLERANCE
    }

    private fun waitForCentre(color: Int) =
        composeRule.waitUntil(timeoutMillis = WAIT_MS) { isNear(color, centrePixel()) }

    @Test
    fun noDetailIsRequestedAtFit() {
        showViewer()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()

        assertTrue(requests.isEmpty())
    }

    @Test
    fun zoomingInLoadsFullResolutionTilesAndDrawsThemOverTheBase() {
        showViewer()

        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("300%")
        waitForCentre(TILE_COLOR)

        assertTrue(
            requests.all { (region, sample) ->
                sample == 1 && region.left + region.width <= PHOTO_WIDTH && region.top + region.height <= PHOTO_HEIGHT
            },
        )
    }

    @Test
    fun zoomingBackToFitDropsTheDetail() {
        showViewer()
        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForCentre(TILE_COLOR)

        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForZoomReadout("100%")
        waitForCentre(BASE_COLOR)
    }

    @Test
    fun draggingToAnotherPartLoadsTheTilesOfThatPart() {
        showViewer()
        composeRule.onNodeWithTag("viewer").performTouchInput { doubleClick(center) }
        waitForCentre(TILE_COLOR)
        val before = requests.map { it.first }.toSet()

        composeRule.onNodeWithTag("viewer").performTouchInput {
            swipe(center, Offset(centerX - width * SWIPE_FRACTION, centerY))
        }

        composeRule.waitUntil(timeoutMillis = WAIT_MS) { requests.any { it.first !in before } }
    }
}
