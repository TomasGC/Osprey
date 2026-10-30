package app.osprey.desktop.viewer

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopOrientationSource
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.desktop.testing.toJpeg
import app.osprey.desktop.testing.toPng
import app.osprey.media.ExifOrientation
import app.osprey.media.HeicSamples
import app.osprey.viewer.TurnSaveResult
import app.osprey.viewer.TurnSaver
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

private const val WIDE = 8
private const val HIGH = 4
private const val RED = 0xFFFF0000.toInt()
private const val RED_MIN = 0xC0
private const val OTHER_MAX = 0x40
private const val RED_SHIFT = 16
private const val BYTE_MASK = 0xFF
private const val UPRIGHT = 1
private const val SIDEWAYS = 6
private const val IROT_AFTER_ONE_TURN = 2
private const val EXIF_AFTER_ONE_TURN = 3
private const val IROT_BOX_AND_ASSOCIATION = 10

private fun isRed(argb: Int): Boolean = (argb shr RED_SHIFT and BYTE_MASK) > RED_MIN && (argb and BYTE_MASK) < OTHER_MAX

/** Saving a turn into real files with the real repository, then reading them back the way the app does (#61). */
class TurnSaverIntegrationRealTest {

    private lateinit var tempDir: Path
    private val files = DesktopFileSystemRepository()
    private val saver = TurnSaver(files)
    private val reader = uprightDesktopMediaMetadataReader()
    private val oldTime = Instant.parse("2020-01-02T03:04:05Z")

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-turn-saver-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().walkTopDown().forEach { it.setWritable(true) }
        tempDir.toFile().deleteRecursively()
    }

    private fun image(): BufferedImage {
        val image = BufferedImage(WIDE, HIGH, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until HIGH / 2) for (x in 0 until WIDE / 2) image.setRGB(x, y, RED)
        return image
    }

    private fun jpeg(name: String, orientation: Int): Path =
        Files.write(tempDir.resolve(name), image().toJpeg(orientation)).also {
            Files.setLastModifiedTime(it, FileTime.from(oldTime))
        }

    private fun save(path: Path, turns: Int) = saver.save(PathHandle(path), path.fileName.toString(), turns)

    @Test
    fun `a saved turn shows upright in the reader the app uses`() {
        val path = jpeg("a.jpg", UPRIGHT)

        assertEquals(TurnSaveResult.Saved, save(path, 1))

        val frame = reader.decodeFrame(PathHandle(path))!!
        assertEquals(HIGH, frame.width)
        assertEquals(WIDE, frame.height)
        assertTrue(isRed(frame.pixels[frame.width - 1]))
        assertEquals(ExifOrientation.ROTATE_90, DesktopOrientationSource().orientationOf(PathHandle(path)))
    }

    @Test
    fun `one byte changes and the size and the modified time stay`() {
        val path = jpeg("a.jpg", UPRIGHT)
        val before = Files.readAllBytes(path)

        save(path, 1)

        val after = Files.readAllBytes(path)
        assertEquals(before.size, after.size)
        assertEquals(1, before.indices.count { before[it] != after[it] })
        assertEquals(oldTime, Files.getLastModifiedTime(path).toInstant())
    }

    @Test
    fun `a sideways photo turned once more is upside down`() {
        val path = jpeg("a.jpg", SIDEWAYS)

        save(path, 1)

        assertEquals(ExifOrientation.ROTATE_180, DesktopOrientationSource().orientationOf(PathHandle(path)))
    }

    @Test
    fun `four saves give back the original bytes`() {
        val path = jpeg("a.jpg", SIDEWAYS)
        val before = Files.readAllBytes(path)

        repeat(4) { save(path, 1) }

        assertArrayEquals(before, Files.readAllBytes(path))
    }

    private fun png(name: String, orientation: Int? = null): Path =
        Files.write(tempDir.resolve(name), image().toPng(orientation)).also {
            Files.setLastModifiedTime(it, FileTime.from(oldTime))
        }

    private fun namesInFolder(): List<String> =
        Files.list(tempDir).use { stream -> stream.map { it.fileName.toString() }.toList() }

    @Test
    fun `a saved png turn shows upright in the reader the app uses`() {
        val path = png("a.png")

        assertEquals(TurnSaveResult.Saved, save(path, 1))

        val frame = reader.decodeFrame(PathHandle(path))!!
        assertEquals(HIGH, frame.width)
        assertEquals(WIDE, frame.height)
        assertTrue(isRed(frame.pixels[frame.width - 1]))
    }

    @Test
    fun `a stored png orientation is baked into the pixels and reset`() {
        val path = png("a.png", SIDEWAYS)
        val before = reader.decodeFrame(PathHandle(path))!!

        save(path, 1)

        val after = reader.decodeFrame(PathHandle(path))!!
        assertEquals(before.height, after.width)
        assertEquals(before.width, after.height)
        for (y in 0 until after.height) {
            for (x in 0 until after.width) {
                assertEquals(before.pixels[(before.height - 1 - x) * before.width + y], after.pixels[y * after.width + x])
            }
        }
        assertEquals(ExifOrientation.NORMAL, DesktopOrientationSource().orientationOf(PathHandle(path)))
    }

    @Test
    fun `a png keeps its modified time and leaves nothing beside it`() {
        val path = png("a.png")

        save(path, 1)

        assertEquals(oldTime, Files.getLastModifiedTime(path).toInstant())
        assertEquals(listOf("a.png"), namesInFolder())
    }

    @Test
    fun `four saved png turns give the original pixels back`() {
        val path = png("a.png")
        val before = reader.decodeFrame(PathHandle(path))!!

        repeat(4) { save(path, 1) }

        val after = reader.decodeFrame(PathHandle(path))!!
        assertEquals(before.width, after.width)
        assertArrayEquals(before.pixels, after.pixels)
    }

    @Test
    fun `a read-only png fails and is untouched`() {
        val path = png("a.png")
        val before = Files.readAllBytes(path)
        path.toFile().setWritable(false)
        assumeFalse("this account can write to read-only files", Files.isWritable(path))

        assertTrue(save(path, 1) is TurnSaveResult.Failed)

        assertArrayEquals(before, Files.readAllBytes(path))
        assertEquals(listOf("a.png"), namesInFolder())
    }

    @Test
    fun `a png that is gone is a failure`() {
        assertTrue(save(tempDir.resolve("gone.png"), 1) is TurnSaveResult.Failed)
    }

    @Test
    fun `a read-only file fails and is untouched`() {
        val path = jpeg("a.jpg", UPRIGHT)
        val before = Files.readAllBytes(path)
        path.toFile().setWritable(false)
        assumeFalse("this account can write to read-only files", Files.isWritable(path))

        assertTrue(save(path, 1) is TurnSaveResult.Failed)

        assertArrayEquals(before, Files.readAllBytes(path))
    }

    private fun heicFile(name: String, sample: HeicSamples.Sample): Path =
        Files.write(tempDir.resolve(name), sample.bytes).also {
            Files.setLastModifiedTime(it, FileTime.from(oldTime))
        }

    @Test
    fun `a heic with a rotation is turned in place and keeps its modified time`() {
        val sample = HeicSamples.rotated
        val path = heicFile("a.heic", sample)

        assertEquals(TurnSaveResult.Saved, save(path, 1))

        val after = Files.readAllBytes(path)
        assertEquals(sample.bytes.size, after.size)
        val changed = sample.bytes.indices.filter { sample.bytes[it] != after[it] }
        assertEquals(listOf(sample.exifValueAt, sample.irotAt!!), changed)
        assertEquals(IROT_AFTER_ONE_TURN, after[sample.irotAt!!].toInt())
        assertEquals(EXIF_AFTER_ONE_TURN, after[sample.exifValueAt].toInt())
        assertEquals(oldTime, Files.getLastModifiedTime(path).toInstant())
    }

    @Test
    fun `a heic with no rotation is replaced by a file ten bytes longer and keeps its modified time`() {
        val path = heicFile("a.heic", HeicSamples.plain)

        assertEquals(TurnSaveResult.Saved, save(path, 1))

        assertEquals(HeicSamples.plain.bytes.size + IROT_BOX_AND_ASSOCIATION, Files.size(path).toInt())
        assertEquals(oldTime, Files.getLastModifiedTime(path).toInstant())
        assertEquals(listOf("a.heic"), namesInFolder())
    }

    @Test
    fun `a rebuilt heic is turned in place the next time`() {
        val path = heicFile("a.heic", HeicSamples.plain)
        save(path, 1)
        val size = Files.size(path)

        assertEquals(TurnSaveResult.Saved, save(path, 3))

        assertEquals(size, Files.size(path))
    }

    @Test
    fun `a read-only heic fails and is untouched, rotated or not`() {
        for (sample in listOf(HeicSamples.rotated, HeicSamples.plain)) {
            val path = heicFile("a.heic", sample)
            path.toFile().setWritable(false)
            assumeFalse("this account can write to read-only files", Files.isWritable(path))

            assertTrue(save(path, 1) is TurnSaveResult.Failed)

            assertArrayEquals(sample.bytes, Files.readAllBytes(path))
            assertEquals(listOf("a.heic"), namesInFolder())
            path.toFile().setWritable(true)
            Files.delete(path)
        }
    }
}
