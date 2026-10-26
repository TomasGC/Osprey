package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
import app.osprey.media.ExifOrientation
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 8 bytes of PNG signature, then the 25-byte IHDR chunk. */
private const val PNG_SIGNATURE_AND_IHDR = 33

class DesktopOrientationSourceTest {

    private lateinit var tempDir: Path
    private val source = DesktopOrientationSource()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-orientation-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun imageBytes(format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), format, it) }
            .toByteArray()

    /** A TIFF block (little endian) holding one tag: Orientation = [value]. */
    private fun tiffWithOrientation(value: Int): ByteArray = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
        .put("II".toByteArray()).putShort(42).putInt(8) // header, IFD0 at offset 8
        .putShort(1) // one entry
        .putShort(0x0112).putShort(3).putInt(1).putShort(value.toShort()).putShort(0) // Orientation, SHORT, count 1
        .putInt(0) // no next IFD
        .array()

    /** A JPEG with an EXIF segment right after the start-of-image marker. */
    private fun jpegWithOrientation(value: Int): ByteArray {
        val jpeg = imageBytes("jpg")
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiffWithOrientation(value)
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, (exif.size + 2).toByte()) + exif
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }

    /** A PNG with an `eXIf` chunk right after the header chunk. */
    private fun pngWithOrientation(value: Int): ByteArray {
        val png = imageBytes("png")
        val tiff = tiffWithOrientation(value)
        val type = "eXIf".toByteArray()
        val crc = CRC32().apply {
            update(type)
            update(tiff)
        }.value.toInt()
        val chunk = ByteBuffer.allocate(12 + tiff.size).putInt(tiff.size).put(type).put(tiff).putInt(crc).array()
        return png.copyOfRange(0, PNG_SIGNATURE_AND_IHDR) + chunk +
            png.copyOfRange(PNG_SIGNATURE_AND_IHDR, png.size)
    }

    private fun handle(name: String, bytes: ByteArray) = PathHandle(Files.write(tempDir.resolve(name), bytes))

    @Test
    fun `a JPEG reports each of the eight orientations it stores`() {
        for (value in 1..8) {
            assertEquals(
                ExifOrientation.fromExif(value),
                source.orientationOf(handle("photo$value.jpg", jpegWithOrientation(value))),
            )
        }
    }

    @Test
    fun `a PNG reports the orientation of its EXIF chunk`() {
        assertEquals(ExifOrientation.ROTATE_90, source.orientationOf(handle("photo.png", pngWithOrientation(6))))
    }

    @Test
    fun `an image with no EXIF is upright`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("plain.jpg", imageBytes("jpg"))))
    }

    @Test
    fun `an unreadable image is upright`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("broken.jpg", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `a HEIC is never turned again, because ffmpeg already applied its rotation`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("photo.heic", jpegWithOrientation(6))))
    }

    @Test
    fun `a video is upright, its frame comes out of ffmpeg already turned`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("clip.mp4", jpegWithOrientation(6))))
    }
}
