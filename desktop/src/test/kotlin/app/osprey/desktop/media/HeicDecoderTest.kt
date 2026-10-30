package app.osprey.desktop.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val OPAQUE = 0xFF000000.toInt()
private const val RED = OPAQUE or 0xFF0000
private const val GREEN = OPAQUE or 0x00FF00
private const val BLUE = OPAQUE or 0x0000FF
private const val WHITE = OPAQUE or 0xFFFFFF

class HeicDecoderTest {

    private val photo = Path.of("photo.heic")

    private fun png(width: Int, height: Int, argb: (Int, Int) -> Int = { _, _ -> WHITE }): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, argb(x, y))
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `the rendered picture becomes the pixels, whole`() {
        val colors = intArrayOf(RED, GREEN, BLUE, WHITE)
        val rendered = png(4, 2) { x, y -> colors[(x + y) % colors.size] }

        val buffer = decodeHeicPixelBuffer(photo, maxSide = null) { rendered }

        assertNotNull(buffer)
        assertEquals(4, buffer!!.width)
        assertEquals(2, buffer.height)
        assertEquals(RED, buffer.pixels[0])
        assertEquals(GREEN, buffer.pixels[1])
        assertEquals(GREEN, buffer.pixels[4])
        assertEquals(BLUE, buffer.pixels[2])
    }

    @Test
    fun `the renderer is asked for the photo it was given`() {
        var asked: Path? = null

        decodeHeicPixelBuffer(photo) { path ->
            asked = path
            png(2, 2)
        }

        assertEquals(photo, asked)
    }

    @Test
    fun `a longest side is subsampled down to the size asked for`() {
        val buffer = decodeHeicPixelBuffer(photo, maxSide = 128) { png(512, 384) }

        assertEquals(128, buffer!!.width)
        assertEquals(96, buffer.height)
    }

    @Test
    fun `a photo that cannot be rendered has no pixels`() {
        assertNull(decodeHeicPixelBuffer(photo) { null })
    }

    @Test
    fun `bytes that are not a picture have no pixels`() {
        assertNull(decodeHeicPixelBuffer(photo) { byteArrayOf(1, 2, 3, 4) })
    }

    @Test
    fun `heic and heif photos are decoded through ffmpeg`() {
        assertEquals(setOf(".heic", ".heif"), app.osprey.actions.heicExtensions)
    }
}
