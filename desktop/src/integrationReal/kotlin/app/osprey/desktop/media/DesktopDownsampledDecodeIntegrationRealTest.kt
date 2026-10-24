package app.osprey.desktop.media

import app.osprey.actions.visual.HASH_DECODE_MAX_SIDE
import app.osprey.actions.visual.VISUAL_HAMMING_THRESHOLD
import app.osprey.actions.visual.phash
import app.osprey.desktop.filesystem.PathHandle
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.max
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val PHOTO_WIDTH = 4000
private const val PHOTO_HEIGHT = 3000
private const val SHAPE_COUNT = 40

/** Decoding at a bounded size (#49): small enough to hash, close enough to the full decode to hash alike. */
class DesktopDownsampledDecodeIntegrationRealTest {

    private val reader = DesktopMediaMetadataReader()
    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-desktop-downsample-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    /** Smooth gradient plus blocks of colour: low-frequency structure like a photo, unlike pure noise. */
    private fun bigJpeg(name: String = "big.jpg"): Path {
        val image = BufferedImage(PHOTO_WIDTH, PHOTO_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(20, 60, 160), PHOTO_WIDTH.toFloat(), PHOTO_HEIGHT.toFloat(), Color(230, 190, 60))
        g.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT)
        val random = java.util.Random(SEED)
        repeat(SHAPE_COUNT) {
            g.color = Color(random.nextInt(COLOR_RANGE), random.nextInt(COLOR_RANGE), random.nextInt(COLOR_RANGE))
            g.fillRect(random.nextInt(PHOTO_WIDTH), random.nextInt(PHOTO_HEIGHT), SHAPE_SIZE, SHAPE_SIZE)
        }
        g.dispose()
        val path = tempDir.resolve(name)
        ImageIO.write(image, "jpg", path.toFile())
        return path
    }

    @Test
    fun `decodeFrame with maxSide returns an image between maxSide and twice maxSide`() {
        val frame = reader.decodeFrame(PathHandle(bigJpeg()), HASH_DECODE_MAX_SIDE)

        assertNotNull(frame)
        val longest = max(frame!!.width, frame.height)
        assertTrue("longest side $longest", longest in HASH_DECODE_MAX_SIDE..(2 * HASH_DECODE_MAX_SIDE))
    }

    @Test
    fun `decodeFrame without maxSide keeps the full resolution`() {
        val frame = reader.decodeFrame(PathHandle(bigJpeg()))

        assertEquals(PHOTO_WIDTH, frame!!.width)
        assertEquals(PHOTO_HEIGHT, frame.height)
    }

    @Test
    fun `decodeFrame never upscales an image already smaller than maxSide`() {
        val path = tempDir.resolve("small.jpg")
        ImageIO.write(BufferedImage(SMALL_SIDE, SMALL_SIDE, BufferedImage.TYPE_INT_RGB), "jpg", path.toFile())

        val frame = reader.decodeFrame(PathHandle(path), HASH_DECODE_MAX_SIDE)

        assertEquals(SMALL_SIDE, frame!!.width)
    }

    @Test
    fun `hash of the downsampled decode stays within the grouping threshold of the full decode`() {
        val handle = PathHandle(bigJpeg())

        val full = phash(reader.decodeFrame(handle)!!)
        val small = phash(reader.decodeFrame(handle, HASH_DECODE_MAX_SIDE)!!)

        val distance = (full xor small).countOneBits()
        assertTrue("distance $distance", distance <= VISUAL_HAMMING_THRESHOLD)
    }

    private companion object {
        const val SEED = 49L
        const val COLOR_RANGE = 256
        const val SHAPE_SIZE = 300
        const val SMALL_SIDE = 64
    }
}
