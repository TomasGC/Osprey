package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real EXIF/image-decode/ffmpeg/ffprobe paths that `test_common.py`'s and
 * `test_visual.py`'s mocked subprocess/PIL tests always stopped short of — the gap this issue's
 * own DoD calls out as the point of putting these implementations behind an integrationReal tier.
 */
class DesktopMediaMetadataReaderIntegrationRealTest {

    private val reader = DesktopMediaMetadataReader()
    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-desktop-media-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun realPlainJpeg(): Path {
        val path = tempDir.resolve("plain.jpg")
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", path.toFile())
        return path
    }

    @Test
    fun `decodeFrame decodes a real plain jpeg`() {
        val frame = reader.decodeFrame(PathHandle(realPlainJpeg()))

        assertNotNull(frame)
        assertEquals(8, frame!!.width)
        assertEquals(8, frame.height)
    }

    @Test
    fun `captureDateTime returns null for a real image with no EXIF`() {
        assertNull(reader.captureDateTime(PathHandle(realPlainJpeg())))
    }

    @Test
    fun `captureDateTime returns null for a non-image file`() {
        val path = tempDir.resolve("fake.jpg")
        Files.write(path, "not actually a jpeg".toByteArray())

        assertNull(reader.captureDateTime(PathHandle(path)))
    }

    @Test
    fun `decodeFrame returns null for a non-image file`() {
        val path = tempDir.resolve("fake.jpg")
        Files.write(path, "not actually a jpeg".toByteArray())

        assertNull(reader.decodeFrame(PathHandle(path)))
    }

    @Test
    fun `captureDateTime returns null for a missing file`() {
        assertNull(reader.captureDateTime(PathHandle(tempDir.resolve("missing.jpg"))))
    }

    @Test
    fun `decodeFrame extracts a real video frame via ffmpeg`() {
        Assume.assumeTrue("ffmpeg not on PATH", isToolOnPath("ffmpeg"))

        val frame = reader.decodeFrame(PathHandle(generateTestVideo("clip.mp4")))

        assertNotNull(frame)
    }

    @Test
    fun `captureDateTime reads the real container creation_time via ffprobe`() {
        Assume.assumeTrue("ffmpeg/ffprobe not on PATH", isToolOnPath("ffmpeg") && isToolOnPath("ffprobe"))
        val video = generateTestVideo("clip.mp4", creationTime = "2020-05-01T10:30:00.000000Z")

        val date = reader.captureDateTime(PathHandle(video))

        assertEquals(LocalDateTime.of(2020, 5, 1, 10, 30, 0), date?.dateTime)
    }

    private fun generateTestVideo(name: String, creationTime: String? = null): Path {
        val path = tempDir.resolve(name)
        val args = mutableListOf(
            "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=red:s=32x32:d=2:r=5", "-pix_fmt", "yuv420p",
        )
        if (creationTime != null) {
            args += listOf("-metadata", "creation_time=$creationTime")
        }
        args += listOf("-frames:v", "10", path.toString())
        ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor()
        return path
    }

    private fun isToolOnPath(tool: String): Boolean = try {
        ProcessBuilder(tool, "-version").redirectErrorStream(true).start().let {
            it.inputStream.readBytes()
            it.waitFor() == 0
        }
    } catch (e: java.io.IOException) {
        false
    }
}
