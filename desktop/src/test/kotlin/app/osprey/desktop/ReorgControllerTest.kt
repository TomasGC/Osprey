package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReorgControllerTest {

    /** No metadata at all: the year comes from the file names. */
    private object NameOnlyReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle): PixelBuffer? = null
    }

    private lateinit var tempDir: Path
    private lateinit var output: Path
    private lateinit var camera: Path
    private lateinit var controller: ReorgController

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-reorg-controller-test")
        output = Files.createDirectory(tempDir.resolve("Library"))
        camera = Files.createDirectory(tempDir.resolve("Camera"))
        val sources = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))
        controller = ReorgController(ReorgPlanner(DesktopFileSystemRepository(), NameOnlyReader)) { sources }
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `apply before any scan does nothing`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")

        runBlocking { controller.onApply(PathHandle(output)) }

        assertTrue(Files.exists(camera.resolve("IMG_20180101_000000.jpg")))
        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `scan lists the plan and enables apply`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")

        runBlocking { controller.onScan(PathHandle(output)) }

        assertTrue(controller.state.applyEnabled)
        assertTrue(controller.state.lines.any { it.contains("MOVE Camera/IMG_20180101_000000.jpg -> 2018/") })
        assertFalse(controller.state.busy)
    }

    @Test
    fun `scan with nothing to do leaves apply disabled`() {
        runBlocking { controller.onScan(PathHandle(output)) }

        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `apply moves the files, reports the result and consumes the plan`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")
        runBlocking { controller.onScan(PathHandle(output)) }

        runBlocking { controller.onApply(PathHandle(output)) }

        assertTrue(Files.exists(output.resolve("2018").resolve("IMG_20180101_000000.jpg")))
        assertFalse(Files.exists(camera.resolve("IMG_20180101_000000.jpg")))
        assertTrue(controller.state.appliedSuffix.contains("APPLIED (1)"))
        assertTrue(controller.state.lines.first().contains("APPLIED copied=0 moved=1"))
        assertFalse(controller.state.applyEnabled)
        assertFalse(controller.state.canCancel)
        assertEquals(null, controller.state.progress)
    }

    @Test
    fun `a stop requested before apply starts does not carry over to the next run`() {
        for (day in 1..3) Files.writeString(camera.resolve("IMG_2018010${day}_000000.jpg"), "file$day")
        runBlocking { controller.onScan(PathHandle(output)) }
        controller.onCancel()

        runBlocking { controller.onApply(PathHandle(output)) }

        assertEquals(3, Files.list(output.resolve("2018")).use { it.count() })
    }
}
