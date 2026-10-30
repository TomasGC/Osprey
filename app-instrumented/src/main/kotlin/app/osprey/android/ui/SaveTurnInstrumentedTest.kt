package app.osprey.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.AndroidFileSystemRepository
import app.osprey.android.media.AndroidOrientationSource
import app.osprey.filesystem.FileHandle
import app.osprey.media.ExifOrientation
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.TurnSaver
import app.osprey.viewer.ViewTurns
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 5_000L
private const val JPEG_QUALITY = 90
private const val PNG_QUALITY = 100
private const val SIZE = 8
private const val RED = 0xFFFF0000.toInt()
private const val BLUE = 0xFF0000FF.toInt()
private const val SAVE = "Save turn"
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

/**
 * Saving a turn from the Android viewer (#61): the real [MediaPreview] and the real [TurnSaver] over the real
 * [AndroidFileSystemRepository], on JPEGs the test generates in the app's cache folder (so no SAF grant is needed).
 * A fake reader counts how often the photo is decoded, since a saved photo must be read again.
 */
@RunWith(AndroidJUnit4::class)
class SaveTurnInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val orientations = AndroidOrientationSource(context)
    private val turns = ViewTurns(TurnSaver(AndroidFileSystemRepository(context))::save)
    private val files = mutableListOf<File>()
    private val decodes = AtomicInteger()

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer {
            decodes.incrementAndGet()
            return ONE_PIXEL
        }
    }

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun jpeg(name: String, orientation: Int?): AndroidFileHandle {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, name).also { files += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        if (orientation != null) {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            exif.saveAttributes()
        }
        return AndroidFileHandle(DocumentFile.fromFile(file))
    }

    /** [SIZE] square, red on the left half and blue on the right: a quarter turn right puts red on top. */
    private fun png(name: String): AndroidFileHandle {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) for (x in 0 until SIZE) bitmap.setPixel(x, y, if (x < SIZE / 2) RED else BLUE)
        val file = File(context.cacheDir, name).also { files += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        // Found through its folder, like every photo the app lists, so the handle knows its parent.
        return AndroidFileHandle(DocumentFile.fromFile(context.cacheDir).findFile(name) ?: error("no $name"))
    }

    private fun showViewer(handle: AndroidFileHandle, name: String) {
        composeRule.setContent {
            MediaPreview(
                reader, turns, handle, name, checked = false, checkLabel = "Keep",
                onCheckedChange = {}, onClose = {},
            )
        }
        waitFor { decodes.get() >= 1 }
    }

    private fun waitFor(condition: () -> Boolean) = composeRule.waitUntil(timeoutMillis = WAIT_MS, condition)

    private fun waitForText(text: String) = waitFor { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun rotateRight() = composeRule.onNodeWithContentDescription("Rotate right").performClick()

    @Test
    fun saveIsOffUntilThePhotoIsTurned() {
        showViewer(jpeg("upright.jpg", ExifInterface.ORIENTATION_NORMAL), "upright.jpg")

        composeRule.onNodeWithContentDescription(SAVE).assertIsNotEnabled()

        rotateRight()

        composeRule.onNodeWithContentDescription(SAVE).assertIsEnabled()
    }

    @Test
    fun savingWritesTheTurnIntoTheFileAndReloadsThePhoto() {
        val handle = jpeg("turn.jpg", ExifInterface.ORIENTATION_NORMAL)
        showViewer(handle, "turn.jpg")
        val decodedBefore = decodes.get()

        rotateRight()
        composeRule.onNodeWithContentDescription(SAVE).performClick()

        waitFor { orientations.orientationOf(handle) == ExifOrientation.ROTATE_90 }
        waitFor { decodes.get() > decodedBefore }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(SAVE).assertIsNotEnabled()
        assertEquals(0, turns.turnsOf(handle))
        assertEquals(1, turns.revisionOf(handle))
    }

    @Test
    fun aTurnedHeicCannotBeSavedAndSaysWhy() {
        showViewer(jpeg("really-a-jpeg-named-heic.heic", ExifInterface.ORIENTATION_NORMAL), "photo.heic")

        rotateRight()

        waitForText(TurnSaver.UNSUPPORTED_FORMAT)
        composeRule.onNodeWithContentDescription(SAVE).assertIsNotEnabled()
    }

    @Test
    fun savingATurnedPngRewritesItAndReloadsThePhoto() {
        val handle = png("turn.png")
        showViewer(handle, "turn.png")
        val decodedBefore = decodes.get()

        rotateRight()
        composeRule.onNodeWithContentDescription(SAVE).performClick()

        waitFor { decodes.get() > decodedBefore }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(SAVE).assertIsNotEnabled()
        val saved = BitmapFactory.decodeFile(files.last().absolutePath)
        assertEquals(RED, saved.getPixel(0, 0))
        assertEquals(BLUE, saved.getPixel(0, SIZE - 1))
        assertEquals(0, turns.turnsOf(handle))
        assertEquals(1, turns.revisionOf(handle))
        assertEquals(listOf("turn.png"), context.cacheDir.list().orEmpty().filter { it.startsWith("turn.png") })
    }

    @Test
    fun aRefusedSaveShowsItsMessageAndKeepsTheTurn() {
        val handle = jpeg("untagged.jpg", null)
        showViewer(handle, "untagged.jpg")

        rotateRight()
        composeRule.onNodeWithContentDescription(SAVE).performClick()

        waitForText(TurnSaver.NO_ORIENTATION_TAG)
        composeRule.onNodeWithContentDescription(SAVE).assertIsEnabled()
        assertTrue(turns.turnsOf(handle) == 1)
    }
}
