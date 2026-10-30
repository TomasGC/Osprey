package app.osprey.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.media.decodeHeicPixelBuffer
import app.osprey.desktop.ui.OspreyApp
import app.osprey.fixtures.Photos
import app.osprey.media.PixelBuffer
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 30_000L
private const val WITH_ROTATION_PROPERTY = "gull_1.heic"
private const val WITHOUT_ROTATION_PROPERTY = "sun_1.heic"

/**
 * Real photos in the real desktop app (#76): a HEIC from the private fixtures is opened in the viewer, turned and
 * saved, and the file ffmpeg decodes afterwards must be the photo it decoded before, a quarter turn to the right.
 * `gull_1` carries a rotation property (patched in place), `sun_1` none (one is inserted); a hand-built HEIC holds no
 * picture, so these are the only desktop tests that see a save change what a decoder shows.
 */
@OptIn(ExperimentalTestApi::class)
class RealPhotoEndToEndTest {

    private lateinit var root: Path

    @Before
    fun setUp() {
        val fixtures = Photos.need()
        root = Files.createTempDirectory("osprey-e2e-real")
        val dedupe = Files.createDirectories(root.resolve("Library").resolve("Trash").resolve("2026").resolve("Dedupe"))
        for (name in listOf(WITH_ROTATION_PROPERTY, WITHOUT_ROTATION_PROPERTY)) {
            Files.copy(fixtures.resolve("heic").resolve(name), dedupe.resolve(name))
        }
        val config = JSONObject()
            .put("output_path", root.resolve("Library").toString())
            .put("input_folders", JSONArray())
        Files.writeString(root.resolve("config.json"), config.toString())
        System.setProperty("osprey.config.path", root.resolve("config.json").toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("osprey.config.path")
        if (::root.isInitialized) root.toFile().deleteRecursively()
    }

    private fun file(name: String) = root.resolve("Library").resolve("Trash").resolve("2026").resolve("Dedupe").resolve(name)

    private fun decoded(name: String): PixelBuffer? = decodeHeicPixelBuffer(file(name))

    /** [this] a quarter turn to the right: what the viewer's "Rotate right" does to the picture. */
    private fun PixelBuffer.turnedRight() = PixelBuffer(
        height,
        width,
        IntArray(pixels.size).also { turned ->
            for (y in 0 until height) for (x in 0 until width) turned[x * height + (height - 1 - y)] = pixels[y * width + x]
        },
    )

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openTrash(name: String) {
        val state = desktopAppState()
        state.start()
        setContent { OspreyApp(state) }
        onNodeWithText("🗑").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.turnAndSave(name: String) {
        val original = Files.readAllBytes(file(name))
        openTrash(name)
        onNodeWithContentDescription(name).performClick()
        waitForText("100%")
        onNodeWithContentDescription("Rotate right").performClick()
        onNodeWithContentDescription("Save turn").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { !Files.readAllBytes(file(name)).contentEquals(original) }
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasContentDescription("Save turn") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun checkSavedTurn(name: String) = runComposeUiTest {
        val before = decoded(name)
        assumeNotNull(before) // ffmpeg is not on PATH
        val originalSize = Files.size(file(name))

        turnAndSave(name)

        val after = decoded(name)
        assertNotNull("$name no longer decodes after the save", after)
        assertEquals("$name: the picture after the save", before!!.turnedRight(), after)
        assertEquals("$name: the file keeps its HEIC signature", "ftyp", String(Files.readAllBytes(file(name)), 4, 4))
        assertTrue("$name shrank: ${Files.size(file(name))} < $originalSize", Files.size(file(name)) >= originalSize)
        assertArrayEquals("$name: nothing else in the folder changed", arrayOf(WITH_ROTATION_PROPERTY, WITHOUT_ROTATION_PROPERTY),
            Files.list(file(name).parent).use { it.map { path -> path.fileName.toString() }.sorted().toList() }.toTypedArray())
    }

    @Test
    fun `saving a turn into a real HEIC that has a rotation property shows the photo turned a quarter`() =
        checkSavedTurn(WITH_ROTATION_PROPERTY)

    @Test
    fun `saving a turn into a real HEIC that has no rotation property shows the photo turned a quarter`() =
        checkSavedTurn(WITHOUT_ROTATION_PROPERTY)
}
