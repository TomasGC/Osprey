package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.toJpeg
import app.osprey.desktop.testing.toPng
import app.osprey.media.ExifOrientation
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DesktopOrientationSourceTest {

    private lateinit var tempDir: Path
    private val source = DesktopOrientationSource()
    private val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-orientation-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun handle(name: String, bytes: ByteArray) = PathHandle(Files.write(tempDir.resolve(name), bytes))

    @Test
    fun `a JPEG reports each of the eight orientations it stores`() {
        for (value in 1..8) {
            assertEquals(
                ExifOrientation.fromExif(value),
                source.orientationOf(handle("photo$value.jpg", image.toJpeg(value))),
            )
        }
    }

    @Test
    fun `a PNG reports the orientation of its EXIF chunk`() {
        assertEquals(ExifOrientation.ROTATE_90, source.orientationOf(handle("photo.png", image.toPng(6))))
    }

    @Test
    fun `an image with no EXIF is upright`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("plain.jpg", image.toJpeg())))
    }

    @Test
    fun `an unreadable image is upright`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("broken.jpg", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `a HEIC is never turned again, because ffmpeg already applied its rotation`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("photo.heic", image.toJpeg(6))))
    }

    @Test
    fun `a video is upright, its frame comes out of ffmpeg already turned`() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(handle("clip.mp4", image.toJpeg(6))))
    }
}
