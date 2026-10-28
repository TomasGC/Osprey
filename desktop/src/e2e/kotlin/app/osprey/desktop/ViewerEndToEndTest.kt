package app.osprey.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.testing.toJpeg
import app.osprey.desktop.ui.OspreyApp
import app.osprey.media.ExifOrientation
import app.osprey.media.JpegOrientation
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 10_000L
private const val RED = 0xFFFF0000.toInt()
private const val BLUE = 0xFF0000FF.toInt()
private const val STRONG = 0.7f
private const val WEAK = 0.35f
private const val WIDE = 300
private const val HIGH = 200
private const val HALF_OF_STORED_HIGH = 150
private const val ROTATE_90 = 6
private const val ROTATE_270 = 8
private const val PLAIN = "plain.jpg"
private const val TAG_6 = "tag6.jpg"
private const val TAG_8 = "tag8.jpg"

/**
 * The desktop app end to end, headless (#51): the real [OspreyApp] on a temp library, with the real file system, the
 * real upright reader and the real settings file (through `osprey.config.path`, so the user's own is never touched).
 * Every photo is red on its left and blue on its right once upright, however it is stored, so a card or a viewer
 * that shows one sideways is caught by its pixels.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerEndToEndTest {

    private lateinit var root: Path

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-e2e")
        val dedupe = Files.createDirectories(root.resolve("Library").resolve("Trash").resolve("2026").resolve("Dedupe"))
        Files.write(dedupe.resolve(PLAIN), uprightRedLeft().toJpeg())
        Files.write(dedupe.resolve(TAG_6), storedSideways(redOnTop = false).toJpeg(ROTATE_90))
        Files.write(dedupe.resolve(TAG_8), storedSideways(redOnTop = true).toJpeg(ROTATE_270))
        val config = JSONObject()
            .put("output_path", root.resolve("Library").toString())
            .put("input_folders", JSONArray())
        Files.writeString(root.resolve("config.json"), config.toString())
        System.setProperty("osprey.config.path", root.resolve("config.json").toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("osprey.config.path")
        root.toFile().deleteRecursively()
    }

    /** [WIDE] x [HIGH], left half red, right half blue: the photo as it looks upright. */
    private fun uprightRedLeft() = image(WIDE, HIGH) { x, _ -> if (x < WIDE / 2) RED else BLUE }

    /** [HIGH] x [WIDE]: the same photo stored turned a quarter, so its halves are on top and bottom. */
    private fun storedSideways(redOnTop: Boolean) =
        image(HIGH, WIDE) { _, y -> if ((y < HALF_OF_STORED_HIGH) == redOnTop) RED else BLUE }

    private fun image(width: Int, height: Int, color: (Int, Int) -> Int) =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, color(x, y))
        }

    private fun ComposeUiTest.openTrash() {
        val state = desktopAppState()
        state.start()
        setContent { OspreyApp(state) }
        onNodeWithText("🗑").performClick()
        waitUntil(timeoutMillis = WAIT_MS) {
            listOf(PLAIN, TAG_6, TAG_8).all { onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.card(name: String): PixelMap = onNodeWithContentDescription(name).captureToImage().toPixelMap()

    private fun PixelMap.at(fromLeft: Double, fromTop: Double): Color = this[(width * fromLeft).toInt(), (height * fromTop).toInt()]

    private fun Color.isRed() = red > STRONG && blue < WEAK

    private fun Color.isBlue() = blue > STRONG && red < WEAK

    @Test
    fun `every photo shows upright in its card, whatever its stored orientation`() = runComposeUiTest {
        openTrash()

        for (name in listOf(PLAIN, TAG_6, TAG_8)) {
            val pixels = card(name)
            assertTrue("$name: left side should be red", pixels.at(0.25, 0.5).isRed())
            assertTrue("$name: right side should be blue", pixels.at(0.75, 0.5).isBlue())
        }
    }

    @Test
    fun `the viewer zooms and turns a photo, and its card follows the turn`() = runComposeUiTest {
        openTrash()

        onNodeWithContentDescription(TAG_6).performClick()
        waitForText("100%")
        onNodeWithTag("viewer").performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        waitForText("125%")
        onNodeWithContentDescription("Rotate right").performClick()
        waitForText("100%")
        onNodeWithText("Close").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithContentDescription(TAG_6).fetchSemanticsNodes().size == 1 }

        val pixels = card(TAG_6)
        assertTrue("after turning right, the red left side is on top", pixels.at(0.5, 0.25).isRed())
        assertTrue("after turning right, the blue right side is at the bottom", pixels.at(0.5, 0.75).isBlue())
    }

    private fun ComposeUiTest.saveButton() = onNodeWithContentDescription("Save turn")

    private fun storedOrientation(name: String) =
        JpegOrientation.read(Files.readAllBytes(dedupeFolder().resolve(name)))

    private fun dedupeFolder() = root.resolve("Library").resolve("Trash").resolve("2026").resolve("Dedupe")

    @Test
    fun `Save turn is off until the photo is turned`() = runComposeUiTest {
        openTrash()

        onNodeWithContentDescription(TAG_6).performClick()
        waitForText("100%")
        saveButton().assertIsNotEnabled()

        onNodeWithContentDescription("Rotate right").performClick()

        saveButton().assertIsEnabled()
    }

    @Test
    fun `saving a turn writes it into the file and the card looks the same afterwards`() = runComposeUiTest {
        openTrash()

        onNodeWithContentDescription(TAG_6).performClick()
        waitForText("100%")
        onNodeWithContentDescription("Rotate right").performClick()
        saveButton().performClick()

        waitUntil(timeoutMillis = WAIT_MS) { storedOrientation(TAG_6) == ExifOrientation.ROTATE_180 }
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasContentDescription("Save turn") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Close").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithContentDescription(TAG_6).fetchSemanticsNodes().size == 1 }

        val pixels = card(TAG_6)
        assertTrue("after the save, the red left side is on top", pixels.at(0.5, 0.25).isRed())
        assertTrue("after the save, the blue right side is at the bottom", pixels.at(0.5, 0.75).isBlue())
    }
}
