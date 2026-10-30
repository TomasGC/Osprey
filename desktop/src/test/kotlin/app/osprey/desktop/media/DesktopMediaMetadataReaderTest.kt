package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
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

class DesktopMediaMetadataReaderTest {

    private lateinit var tempDir: Path
    private val reader = DesktopMediaMetadataReader()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-reader-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun file(name: String) = PathHandle(Files.write(tempDir.resolve(name), byteArrayOf(1, 2, 3)))

    @Test
    fun `a file that is neither an image nor a video has no capture date`() {
        assertNull(reader.captureDateTime(file("notes.txt")))
    }

    @Test
    fun `a file that is neither an image nor a video has no frame`() {
        assertNull(reader.decodeFrame(file("notes.txt"), maxSide = null))
        assertNull(reader.decodeFrame(file("README"), maxSide = 128))
    }

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `a HEIC frame comes from the renderer, not from ImageIO`() {
        val heicReader = DesktopMediaMetadataReader(HeicRenderer { png(6, 4) })

        val frame = heicReader.decodeFrame(file("IMG_0001.HEIC"), maxSide = null)

        assertEquals(6, frame!!.width)
        assertEquals(4, frame.height)
    }

    @Test
    fun `a HEIC that cannot be rendered has no frame`() {
        val heicReader = DesktopMediaMetadataReader(HeicRenderer { null })

        assertNull(heicReader.decodeFrame(file("IMG_0001.heic"), maxSide = 512))
    }

    @Test
    fun `an image that is not a HEIC never reaches the renderer`() {
        val heicReader = DesktopMediaMetadataReader(HeicRenderer { error("not for a JPEG") })

        assertNull(heicReader.decodeFrame(file("broken.jpg"), maxSide = null))
    }

    @Test
    fun `an image that cannot be read has no capture date and no frame`() {
        val broken = file("broken.JPG")

        assertNull(reader.captureDateTime(broken))
        assertNull(reader.decodeFrame(broken, maxSide = null))
    }

    @Test
    fun `a HEIF frame comes from the renderer too`() {
        val heifReader = DesktopMediaMetadataReader(HeicRenderer { png(6, 4) })

        val frame = heifReader.decodeFrame(file("IMG_0001.HEIF"), maxSide = null)

        assertEquals(6, frame!!.width)
        assertEquals(4, frame.height)
    }
}
