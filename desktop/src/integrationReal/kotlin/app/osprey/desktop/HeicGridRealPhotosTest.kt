package app.osprey.desktop

import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.decodeHeicPixelBuffer
import app.osprey.desktop.media.HeicRenderer
import app.osprey.desktop.media.heicGridCommand
import app.osprey.desktop.media.heicRenderCommand
import app.osprey.desktop.media.parseHeicGrid
import app.osprey.desktop.media.probeHeicGroups
import app.osprey.desktop.media.runFfmpeg
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.fixtures.Photos
import app.osprey.media.HeicRotation
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

private const val STORED_WIDTH = 4000
private const val STORED_HEIGHT = 3000

/**
 * Real tile-grid HEICs through the real ffmpeg (#76). Every fixture is a 4000 x 3000 grid of 512 px tiles; an `irot`
 * of an odd number of quarter turns shows it as 3000 x 4000. A distro ffmpeg before 8.1 decodes only the first tile
 * of such a file, so the app must compose the grid itself; a newer one composes it, and then both must agree.
 */
class HeicGridRealPhotosTest {

    private fun firstOfEachSubject(): List<Path> {
        val fixtures = Photos.need()
        return Photos.png(fixtures).map { fixtures.resolve("heic").resolve(it.fileName.toString().replace(".png", ".heic")) }
    }

    private fun turnsOf(photo: Path) = HeicRotation.of(Files.readAllBytes(photo))

    @Test
    fun `the app decodes every real HEIC at its full size and upright`() {
        val reader = uprightDesktopMediaMetadataReader()
        for (photo in firstOfEachSubject()) {
            val frame = reader.decodeFrame(PathHandle(photo), null)
            assertNotNull("${photo.fileName} did not decode", frame)
            val sideways = turnsOf(photo) % 2 == 1
            val expected = if (sideways) STORED_HEIGHT to STORED_WIDTH else STORED_WIDTH to STORED_HEIGHT
            assertEquals("${photo.fileName}", expected, frame!!.width to frame.height)
        }
    }

    @Test
    fun `the composed picture is the one an ffmpeg that composes grids itself gives`() {
        for (photo in firstOfEachSubject()) {
            val plain = decodeHeicPixelBuffer(photo, null, HeicRenderer { runFfmpeg(heicRenderCommand(it)) })
            val sideways = turnsOf(photo) % 2 == 1
            val expectedWidth = if (sideways) STORED_HEIGHT else STORED_WIDTH
            assumeTrue("this ffmpeg does not compose tile grids itself", plain?.width == expectedWidth)

            val grid = parseHeicGrid(probeHeicGroups(photo)!!)
            assertNotNull("${photo.fileName} has no tile grid", grid)
            val composed = decodeHeicPixelBuffer(photo, null, HeicRenderer { runFfmpeg(heicGridCommand(it, grid!!, turnsOf(it))) })

            assertEquals("${photo.fileName}", plain, composed)
        }
    }
}
