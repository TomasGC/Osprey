package app.osprey.desktop.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PixelBufferCodecTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-codec-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, 0x336699)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private val garbage = "this is not an image".toByteArray()

    @Test
    fun `decodeImagePixelBuffer reads a png file at full resolution`() {
        val file = tempDir.resolve("a.png")
        Files.write(file, pngBytes(40, 30))

        val buffer = decodeImagePixelBuffer(file)

        assertEquals(40, buffer?.width)
        assertEquals(30, buffer?.height)
        assertEquals(0xFF336699.toInt(), buffer?.pixels?.first())
    }

    @Test
    fun `decodeImagePixelBuffer returns null for a missing file at either size`() {
        val missing = tempDir.resolve("missing.png")

        assertNull(decodeImagePixelBuffer(missing))
        assertNull(decodeImagePixelBuffer(missing, maxSide = 128))
    }

    @Test
    fun `decodeImagePixelBuffer returns null for a file that is not an image at either size`() {
        val file = tempDir.resolve("notes.png")
        Files.write(file, garbage)

        assertNull(decodeImagePixelBuffer(file))
        assertNull(decodeImagePixelBuffer(file, maxSide = 128))
    }

    @Test
    fun `decodePngPixelBuffer reads png bytes at full resolution`() {
        val buffer = decodePngPixelBuffer(pngBytes(8, 6))

        assertEquals(8, buffer?.width)
        assertEquals(6, buffer?.height)
    }

    @Test
    fun `decodePngPixelBuffer subsamples to the requested side`() {
        val buffer = decodePngPixelBuffer(pngBytes(400, 300), maxSide = 100)

        assertEquals(true, (buffer?.width ?: 0) in 100..200)
    }

    @Test
    fun `decodePngPixelBuffer returns null for bytes that are not an image at either size`() {
        assertNull(decodePngPixelBuffer(garbage))
        assertNull(decodePngPixelBuffer(garbage, maxSide = 128))
    }
}
