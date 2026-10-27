package app.osprey.android.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.media.PhotoRegion
import app.osprey.media.PhotoSize
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val WIDE = 4000
private const val HIGH = 3000
private const val BLOCK = 500
private const val JPEG_QUALITY = 95
private const val TOLERANCE = 16
private const val ORIENTATION_ROTATE_90 = 6
private const val STUB_SIZE = 64

/**
 * Region decoding (#60) against real JPEGs the test generates: a 4000 x 3000 photo cut in 500 px blocks of one colour
 * each, so a region inside a block has a colour that says exactly where it came from. The files live in the app's
 * cache folder: no SAF grant is needed, so these always run.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRegionDecodeInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = AndroidMediaMetadataReader(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun blockColor(column: Int, row: Int) = Color.rgb(30 + 28 * column, 30 + 40 * row, 128)

    private fun handleOf(file: File) = AndroidFileHandle(DocumentFile.fromFile(file.also { files += it }))

    private fun jpeg(name: String, orientation: Int? = null): AndroidFileHandle {
        val bitmap = Bitmap.createBitmap(WIDE, HIGH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (column in 0 until WIDE / BLOCK) for (row in 0 until HIGH / BLOCK) {
            paint.color = blockColor(column, row)
            canvas.drawRect(
                (column * BLOCK).toFloat(),
                (row * BLOCK).toFloat(),
                ((column + 1) * BLOCK).toFloat(),
                ((row + 1) * BLOCK).toFloat(),
                paint,
            )
        }
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        if (orientation != null) {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            exif.saveAttributes()
        }
        return handleOf(file)
    }

    private fun assertNear(expected: Int, actual: Int) {
        val gaps = listOf(
            abs(Color.red(expected) - Color.red(actual)),
            abs(Color.green(expected) - Color.green(actual)),
            abs(Color.blue(expected) - Color.blue(actual)),
        )
        assertTrue(
            "expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}",
            gaps.all { it <= TOLERANCE },
        )
    }

    @Test
    fun aJpegReportsItsStoredSize() = assertEquals(PhotoSize(WIDE, HIGH), reader.imageSize(jpeg("size.jpg")))

    @Test
    fun aRegionIsDecodedAtRealPixels() {
        val tile = reader.decodeRegion(jpeg("region.jpg"), PhotoRegion(1100, 600, 200, 200), 1)

        assertNotNull(tile)
        assertEquals(200, tile!!.width)
        assertEquals(200, tile.height)
        assertNear(blockColor(2, 1), tile.pixels[tile.width * 100 + 100])
    }

    @Test
    fun aRegionIsSubsampled() {
        val tile = reader.decodeRegion(jpeg("sample.jpg"), PhotoRegion(2100, 1600, 400, 400), 4)!!

        assertEquals(100, tile.width)
        assertEquals(100, tile.height)
        assertNear(blockColor(4, 3), tile.pixels[tile.width * 50 + 50])
    }

    @Test
    fun aRegionWithNoOverlapWithThePhotoIsNull() =
        assertNull(reader.decodeRegion(jpeg("outside.jpg"), PhotoRegion(WIDE, HIGH, 200, 200), 1))

    @Test
    fun aVideoHasNoSizeAndNoRegion() {
        // A readable JPEG under a video name: only the extension says it is not a photo.
        jpeg("disguised.jpg")
        val bytes = File(context.cacheDir, "disguised.jpg").readBytes()
        val handle = handleOf(File(context.cacheDir, "clip.mp4").also { it.writeBytes(bytes) })

        assertNull(reader.imageSize(handle))
        assertNull(reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 1))
    }

    @Test
    fun anUnreadableImageHasNoSizeAndNoRegion() {
        val handle = handleOf(File(context.cacheDir, "broken.jpg").also { it.writeBytes(ByteArray(STUB_SIZE)) })

        assertNull(reader.imageSize(handle))
        assertNull(reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 1))
    }

    /** Stored 4000 x 3000 with Orientation 6: upright 3000 x 4000; its top-left corner is the stored bottom-left. */
    @Test
    fun theAppReaderMapsAnUprightRegionThroughTheOrientation() {
        val handle = jpeg("sideways.jpg", ORIENTATION_ROTATE_90)
        val upright = uprightAndroidMediaMetadataReader(context)

        assertEquals(PhotoSize(HIGH, WIDE), upright.imageSize(handle))
        val tile = upright.decodeRegion(handle, PhotoRegion(100, 100, 200, 200), 1)!!

        assertEquals(200, tile.width)
        assertNear(blockColor(0, 5), tile.pixels[tile.width * 100 + 100])
    }
}
