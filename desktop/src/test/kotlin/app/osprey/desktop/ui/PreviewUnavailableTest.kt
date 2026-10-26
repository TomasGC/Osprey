package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

private const val WAIT_MS = 5_000L
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

@OptIn(ExperimentalTestApi::class)
class PreviewUnavailableTest {

    private val decodes = AtomicInteger()

    private fun readerReturning(frame: PixelBuffer?) = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
            decodes.incrementAndGet()
            return frame
        }
    }

    private fun handle(name: String) = PathHandle(Path.of(name))

    private fun ComposeUiTest.card(name: String, frame: PixelBuffer?) {
        setContent {
            MediaCard(
                readerReturning(frame), handle(name), name, sizeBytes = 1_000L, checked = false,
                onToggle = {}, onOpen = {},
            )
        }
    }

    private fun ComposeUiTest.preview(frame: PixelBuffer?) {
        setContent {
            MediaPreview(
                readerReturning(frame), handle("IMG_0001.heic"), "IMG_0001.heic", checked = false,
                checkLabel = "Keep", onCheckedChange = {}, onClose = {},
            )
        }
    }

    private fun ComposeUiTest.waitForDecode() {
        waitUntil(timeoutMillis = WAIT_MS) { decodes.get() >= 1 }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForPlaceholder() = waitUntil(timeoutMillis = WAIT_MS) {
        onAllNodesWithText(PREVIEW_UNAVAILABLE).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `a card whose photo cannot be decoded says so`() = runComposeUiTest {
        card("IMG_0001.heic", frame = null)

        waitForPlaceholder()
        onNodeWithText(PREVIEW_UNAVAILABLE).assertExists()
    }

    @Test
    fun `a card whose photo decodes shows no placeholder`() = runComposeUiTest {
        card("IMG_0001.heic", frame = ONE_PIXEL)

        waitForDecode()
        onNodeWithText(PREVIEW_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `a video whose frame cannot be read keeps its play button and shows no placeholder`() = runComposeUiTest {
        card("clip.mp4", frame = null)

        waitForDecode()
        onNodeWithText(PREVIEW_UNAVAILABLE).assertDoesNotExist()
        onNodeWithText("▶").assertExists()
    }

    @Test
    fun `a preview whose photo cannot be decoded says so`() = runComposeUiTest {
        preview(frame = null)

        waitForPlaceholder()
        onNodeWithText(PREVIEW_UNAVAILABLE).assertExists()
    }

    @Test
    fun `a preview whose photo decodes shows no placeholder`() = runComposeUiTest {
        preview(frame = ONE_PIXEL)

        waitForDecode()
        onNodeWithText(PREVIEW_UNAVAILABLE).assertDoesNotExist()
    }
}
