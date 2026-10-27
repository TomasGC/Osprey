package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.toJpeg
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WIDE = 8
private const val HIGH = 4
private const val ORIENTATION_ROTATE_90 = 6
private const val RED = 0xFFFF0000.toInt()
private const val RED_MIN = 0xC0
private const val OTHER_MAX = 0x40
private const val RED_SHIFT = 16
private const val BYTE_MASK = 0xFF

/** JPEG is lossy, so "red" means a strong red channel and weak blue, not an exact value. */
private fun isRed(argb: Int): Boolean = (argb shr RED_SHIFT and BYTE_MASK) > RED_MIN && (argb and BYTE_MASK) < OTHER_MAX

/** The reader the app uses, on real files: a photo stored sideways comes out upright (#51). */
class UprightDesktopReaderIntegrationRealTest {

    private lateinit var tempDir: Path
    private val reader = uprightDesktopMediaMetadataReader()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-upright-reader-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    /** A [WIDE] x [HIGH] JPEG, red in its top-left quarter, tagged with EXIF orientation [value]. */
    private fun jpegWithOrientation(name: String, value: Int): PathHandle {
        val image = BufferedImage(WIDE, HIGH, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until HIGH / 2) for (x in 0 until WIDE / 2) image.setRGB(x, y, RED)
        return PathHandle(Files.write(tempDir.resolve(name), image.toJpeg(value)))
    }

    @Test
    fun `a photo stored sideways is decoded upright`() {
        val frame = reader.decodeFrame(jpegWithOrientation("sideways.jpg", ORIENTATION_ROTATE_90))!!

        assertEquals(HIGH, frame.width)
        assertEquals(WIDE, frame.height)
    }

    @Test
    fun `a clockwise turn moves the top-left corner to the top-right`() {
        val frame = reader.decodeFrame(jpegWithOrientation("corner.jpg", ORIENTATION_ROTATE_90))!!
        val topRight = frame.pixels[frame.width - 1]

        assertTrue("expected red, got ${topRight.toUInt().toString(16)}", isRed(topRight))
    }

    @Test
    fun `an upright photo keeps its shape`() {
        val frame = reader.decodeFrame(jpegWithOrientation("upright.jpg", 1))!!

        assertEquals(WIDE, frame.width)
        assertEquals(HIGH, frame.height)
    }
}
