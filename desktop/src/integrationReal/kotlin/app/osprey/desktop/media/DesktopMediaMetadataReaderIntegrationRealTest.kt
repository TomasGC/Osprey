package app.osprey.desktop.media

import app.osprey.actions.dates.DatesPlanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

// Layout of the hand-built EXIF segment: TIFF header (8 bytes), IFD0 with one entry pointing at
// the Exif IFD (26), which holds one DateTimeOriginal entry whose ASCII value follows it (44).
private const val TIFF_MAGIC: Short = 42
private const val TIFF_HEADER_SIZE = 8
private const val EXIF_IFD_OFFSET = 26
private const val TIFF_DATA_OFFSET = 44
private const val TAG_EXIF_IFD_POINTER: Short = 0x8769.toShort()
private const val TAG_DATETIME_ORIGINAL: Short = 0x9003.toShort()
private const val TYPE_ASCII: Short = 2
private const val TYPE_LONG: Short = 4
private const val SOI_SIZE = 2
private const val APP1_LENGTH_FIELD = 2
private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)

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

        // The container time is UTC, so it comes back as this machine's local time.
        assertEquals(utcToLocal(LocalDateTime.of(2020, 5, 1, 10, 30, 0)), date?.dateTime)
    }

    @Test
    fun `Dates falls back to the filename for a real jpeg whose EXIF date is all zeros`() {
        // Cameras with an unset clock write "0000:00:00 00:00:00"; metadata-extractor parses that
        // to year 0, which used to be written as the file's timestamp (1677 on Windows).
        Files.write(tempDir.resolve("IMG_20230101_000000.jpg"), jpegWithExifDateTimeOriginal("0000:00:00 00:00:00"))
        val planner = DatesPlanner(DesktopFileSystemRepository(), reader)

        val fix = planner.plan(PathHandle(tempDir)).fixes.single()

        assertEquals(LocalDateTime.of(2023, 1, 1, 0, 0, 0), fix.dateTime)
        assertEquals(MediaDateSource.FILENAME, fix.source)
    }

    /** A real JPEG with a hand-built little-endian EXIF segment holding only DateTimeOriginal. */
    private fun jpegWithExifDateTimeOriginal(exifDate: String): ByteArray {
        val jpeg = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", it)
        }.toByteArray()
        val date = (exifDate + "\u0000").toByteArray(Charsets.US_ASCII)
        val tiff = ByteBuffer.allocate(TIFF_DATA_OFFSET + date.size).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put('I'.code.toByte()).put('I'.code.toByte()).putShort(TIFF_MAGIC).putInt(TIFF_HEADER_SIZE)
        tiff.putShort(1).putShort(TAG_EXIF_IFD_POINTER).putShort(TYPE_LONG).putInt(1).putInt(EXIF_IFD_OFFSET)
        tiff.putInt(0)
        tiff.putShort(1).putShort(TAG_DATETIME_ORIGINAL).putShort(TYPE_ASCII).putInt(date.size).putInt(TIFF_DATA_OFFSET)
        tiff.putInt(0)
        tiff.put(date)
        val app1 = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xFF.toByte(), 0xE1.toByte()))
            val length = APP1_LENGTH_FIELD + EXIF_HEADER.size + tiff.capacity()
            write(byteArrayOf((length shr Byte.SIZE_BITS).toByte(), length.toByte()))
            write(EXIF_HEADER)
            write(tiff.array())
        }.toByteArray()
        return jpeg.copyOfRange(0, SOI_SIZE) + app1 + jpeg.copyOfRange(SOI_SIZE, jpeg.size)
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
