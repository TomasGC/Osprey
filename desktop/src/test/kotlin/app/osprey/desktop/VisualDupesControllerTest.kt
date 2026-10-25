package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualDupesControllerTest {

    /** Every file decodes to the same small picture, so any two images are visual duplicates. */
    private object SamePictureReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer =
            PixelBuffer(SIDE, SIDE, IntArray(SIDE * SIDE) { it * STEP })
    }

    private lateinit var library: Path
    private lateinit var year: Path
    private lateinit var controller: VisualDupesController

    private val yearDir get() = PathHandle(year)

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-visual-controller-test")
        year = Files.createDirectory(library.resolve("2019"))
        controller = VisualDupesController(
            DesktopFileSystemRepository(),
            SamePictureReader,
            immediateJobQueue(),
        ) { TrashLocation(PathHandle(library), "2019") }
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun scanTwoCopies(): VisualDupeGroupUi {
        Files.writeString(year.resolve("a.jpg"), "aaaa")
        Files.writeString(year.resolve("b.jpg"), "bb")
        controller.onScan(yearDir)
        return controller.groupsIn(yearDir).single()
    }

    @Test
    fun `a scan groups the look-alike images of the folder it ran in`() {
        val group = scanTwoCopies()

        assertEquals(setOf("a.jpg", "b.jpg"), group.members.map { it.name }.toSet())
        assertFalse(controller.scanning)
    }

    @Test
    fun `the groups are not handed out for any other folder`() {
        scanTwoCopies()

        assertTrue(controller.groupsIn(PathHandle(library)).isEmpty())
    }

    @Test
    fun `moving the unticked image sends it to the Trash and marks the group done`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false

        controller.moveUnchecked(yearDir, group)

        assertTrue(Files.exists(year.resolve("a.jpg")))
        assertTrue(Files.exists(library.resolve("Trash").resolve("2019").resolve("VisualDupes").resolve("b.jpg")))
        assertTrue(group.done)
        assertEquals("Moved 1", group.status)
        assertFalse(group.moving)
    }

    @Test
    fun `with nothing ticked nothing moves and the group says to keep one`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = false
        group.keep["b.jpg"] = false

        controller.moveUnchecked(yearDir, group)

        assertEquals("Keep at least one image.", group.status)
        assertTrue(Files.exists(year.resolve("b.jpg")))
    }

    @Test
    fun `with everything ticked nothing moves`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = true

        controller.moveUnchecked(yearDir, group)

        assertEquals("Nothing unchecked -- nothing to move.", group.status)
        assertFalse(group.done)
    }

    @Test
    fun `a group that is done is not moved again`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        controller.moveUnchecked(yearDir, group)
        Files.writeString(year.resolve("b.jpg"), "back again")

        controller.moveUnchecked(yearDir, group)

        assertTrue(Files.exists(year.resolve("b.jpg")))
    }

    @Test
    fun `a file that is already gone is reported as skipped`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        Files.delete(year.resolve("b.jpg"))

        controller.moveUnchecked(yearDir, group)

        assertEquals("Moved 0 (skipped: b.jpg)", group.status)
    }

    private companion object {
        const val SIDE = 8
        const val STEP = 3
    }
}
