package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.visual.VisualDupePlanner
import app.osprey.actions.visual.moveUncheckedToTrash
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.random.Random
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val IMAGE_SIZE = 256

/**
 * Runs #8's real [VisualDupePlanner] and [moveUncheckedToTodeleteDups] against real JPEGs
 * -- the spot-check #12's DoD asks for, for the WhatsApp-recompression case specifically: same
 * picture, re-encoded at a different JPEG quality, must still cluster as a visual dupe. The
 * burst-set case (a real camera burst, e.g. the POC's zoo-bears set) needs the real library on a
 * real machine, same limitation #11 already flagged for manual UI validation.
 */
class VisualDupeEndToEndRealTest {

    private val fs = DesktopFileSystemRepository()
    private val mediaMetadataReader = DesktopMediaMetadataReader()
    private lateinit var libraryDir: Path
    private lateinit var yearDir: Path

    @Before
    fun setUp() {
        libraryDir = Files.createTempDirectory("osprey-visual-dupe-e2e-real-test")
        yearDir = Files.createDirectory(libraryDir.resolve("2019"))
    }

    @After
    fun tearDown() {
        libraryDir.toFile().deleteRecursively()
    }

    @Test
    fun `a recompressed copy clusters with the original, an unrelated image does not`() {
        val original = photoLikeImage(seed = 1)
        writeJpeg(yearDir.resolve("original.jpg"), original, quality = 0.95f)
        writeJpeg(yearDir.resolve("whatsapp_recompressed.jpg"), original, quality = 0.4f)
        writeJpeg(yearDir.resolve("unrelated.jpg"), photoLikeImage(seed = 2), quality = 0.95f)

        val root = PathHandle(yearDir)
        val plan = VisualDupePlanner(fs, mediaMetadataReader).plan(root)

        assertEquals(1, plan.groups.size)
        val group = plan.groups.single()
        assertEquals(setOf("original.jpg", "whatsapp_recompressed.jpg"), group.map { it.name }.toSet())

        val keepName = group.maxBy { it.sizeBytes }.name
        val trash = TrashLocation(PathHandle(libraryDir), "2019")
        val result = moveUncheckedToTrash(fs, trash, root, group, setOf(keepName))

        assertEquals(1, result.moved.size)
        val trashed = libraryDir.resolve("Trash").resolve("2019").resolve("VisualDupes")
        assertTrue(Files.exists(trashed.resolve(result.moved.single())))
        assertTrue(Files.exists(yearDir.resolve("unrelated.jpg")))
    }

    /**
     * A busy, textured image (random-colored tiles), not a flat gradient: phash's median
     * threshold needs a real spread of DCT coefficients to stay stable under recompression, and
     * a flat gradient is close to the worst case for that -- real photos are never this smooth.
     */
    private fun photoLikeImage(seed: Int): BufferedImage {
        val image = BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        val random = Random(seed)
        val tiles = 16
        val tileSize = IMAGE_SIZE / tiles
        for (ty in 0 until tiles) {
            for (tx in 0 until tiles) {
                g.color = Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                g.fillRect(tx * tileSize, ty * tileSize, tileSize, tileSize)
            }
        }
        g.dispose()
        return image
    }

    private fun writeJpeg(path: Path, image: BufferedImage, quality: Float) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam
        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality
        ImageIO.createImageOutputStream(path.toFile()).use { output ->
            writer.output = output
            writer.write(null, IIOImage(image, null, null), param)
        }
        writer.dispose()
    }
}
