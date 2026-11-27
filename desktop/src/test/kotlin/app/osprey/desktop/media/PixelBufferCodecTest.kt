package app.osprey.desktop.media

import app.osprey.desktop.testing.TimedTest
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

class PixelBufferCodecTest : TimedTest() {

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

    /** A picture whose every pixel differs from its neighbours, with sides no subsampling step divides. */
    private fun gradient(format: String): ByteArray {
        val image = BufferedImage(641, 479, BufferedImage.TYPE_3BYTE_BGR)
        for (x in 0 until image.width) for (y in 0 until image.height) {
            image.setRGB(x, y, ((x % 256) shl 16) or ((y % 256) shl 8) or ((x * 7 + y * 3) % 256))
        }
        return ByteArrayOutputStream().also { assertEquals(true, ImageIO.write(image, format, it)) }.toByteArray()
    }

    @Test
    fun `a BMP and a PNG of one picture decode to the same pixels, whole and subsampled`() {
        val bmp = gradient("bmp")
        val png = gradient("png")

        for (maxSide in listOf(null, 400, 128, 100, 37)) {
            assertEquals("$maxSide", decodeBytesPixelBuffer(png, maxSide), decodeBytesPixelBuffer(bmp, maxSide))
        }
    }

    @Test
    fun `bytes are decoded without ImageIO's cache on disk`() {
        val png = pngBytes(8, 6)
        val gone = Files.createDirectory(tempDir.resolve("cache")).toFile()
        val useCache = ImageIO.getUseCache()
        val directory = ImageIO.getCacheDirectory()
        try {
            ImageIO.setUseCache(true)
            ImageIO.setCacheDirectory(gone)
            assertEquals(true, gone.delete())

            assertEquals(8, decodeBytesPixelBuffer(png)?.width)
            assertEquals(8, decodeBytesPixelBuffer(png, maxSide = 4)?.let { it.width * 2 })
        } finally {
            ImageIO.setCacheDirectory(directory)
            ImageIO.setUseCache(useCache)
        }
    }

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
    fun `decodeBytesPixelBuffer reads png bytes at full resolution`() {
        val buffer = decodeBytesPixelBuffer(pngBytes(8, 6))

        assertEquals(8, buffer?.width)
        assertEquals(6, buffer?.height)
    }

    @Test
    fun `decodeBytesPixelBuffer subsamples to the requested side`() {
        val buffer = decodeBytesPixelBuffer(pngBytes(400, 300), maxSide = 100)

        assertEquals(true, (buffer?.width ?: 0) in 100..200)
    }

    @Test
    fun `decodeBytesPixelBuffer returns null for bytes that are not an image at either size`() {
        assertNull(decodeBytesPixelBuffer(garbage))
        assertNull(decodeBytesPixelBuffer(garbage, maxSide = 128))
    }
}
