package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReorgControllerTest : TimedTest() {

    /** No metadata at all: the year comes from the file names. */
    private object NameOnlyReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? = null
    }

    private lateinit var tempDir: Path
    private lateinit var output: Path
    private lateinit var camera: Path
    private lateinit var sources: List<ReorgSource>
    private lateinit var controller: ReorgController
    private var applied = 0

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-reorg-controller-test")
        output = Files.createDirectory(tempDir.resolve("Library"))
        camera = Files.createDirectory(tempDir.resolve("Camera"))
        sources = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))
        controller = controllerOn(immediateJobQueue())
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun controllerOn(jobs: JobQueue) =
        ReorgController(ReorgPlanner(DesktopFileSystemRepository(), NameOnlyReader), jobs, { sources }) { applied++ }

    @Test
    fun `apply before any scan does nothing`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")

        controller.onApply(PathHandle(output))

        assertTrue(Files.exists(camera.resolve("IMG_20180101_000000.jpg")))
        assertFalse(controller.state.applyEnabled)
        assertEquals(0, applied)
    }

    @Test
    fun `scan lists the plan and enables apply`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")

        controller.onScan(PathHandle(output))

        assertTrue(controller.state.applyEnabled)
        assertTrue(controller.state.lines.any { it.contains("MOVE Camera/IMG_20180101_000000.jpg -> 2018/") })
        assertFalse(controller.state.busy)
    }

    @Test
    fun `scan with nothing to do leaves apply disabled`() {
        controller.onScan(PathHandle(output))

        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `apply moves the files, reports the result, consumes the plan and tells the app`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")
        controller.onScan(PathHandle(output))

        controller.onApply(PathHandle(output))

        assertTrue(Files.exists(output.resolve("2018").resolve("IMG_20180101_000000.jpg")))
        assertFalse(Files.exists(camera.resolve("IMG_20180101_000000.jpg")))
        assertTrue(controller.state.appliedSuffix.contains("APPLIED (1)"))
        assertTrue(controller.state.lines.first().contains("APPLIED copied=0 moved=1"))
        assertFalse(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `an apply stopped while it still waits leaves the files and frees the buttons`() {
        Files.writeString(camera.resolve("IMG_20180101_000000.jpg"), "a")
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val gate = CountDownLatch(1)
        try {
            val onRealQueue = controllerOn(jobs)
            onRealQueue.onScan(PathHandle(output))
            waitUntilIdle(jobs)
            jobs.enqueue("Blocker") {
                gate.await(WAIT_SECONDS, TimeUnit.SECONDS)
                JobOutcome("done")
            }

            onRealQueue.onApply(PathHandle(output))
            jobs.stop(jobs.entries.value.single { it.name == "Reorg apply" }.id)
            gate.countDown()
            waitUntilIdle(jobs)

            assertTrue(Files.exists(camera.resolve("IMG_20180101_000000.jpg")))
            assertFalse(onRealQueue.state.busy)
            assertEquals(1, applied)
        } finally {
            gate.countDown()
        }
    }

    private fun namesIn(folder: Path): Set<String> {
        if (!Files.isDirectory(folder)) return emptySet()
        return Files.list(folder).use { list -> list.map { it.fileName.toString() }.toList().toSet() }
    }

    /** The library holds 2018/IMG_20180101_000000.jpg; the Move input holds the same bytes under another name. */
    private fun duplicateInLibrary() {
        Files.writeString(Files.createDirectory(output.resolve("2018")).resolve("IMG_20180101_000000.jpg"), "a")
        Files.writeString(camera.resolve("IMG_20180101_000001.jpg"), "a")
    }

    @Test
    fun `scan lists a Move file already in the library as a duplicate and enables apply`() {
        duplicateInLibrary()

        controller.onScan(PathHandle(output))

        assertTrue(controller.state.applyEnabled)
        assertTrue(controller.state.lines.any { it.contains("DUPLICATE Camera/IMG_20180101_000001.jpg == 2018/") })
    }

    @Test
    fun `apply with a duplicate opens the review and moves nothing`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))

        controller.onApply(PathHandle(output))

        assertTrue(controller.review.view.value != null)
        assertEquals(setOf("IMG_20180101_000001.jpg"), namesIn(camera))
        assertEquals(0, applied)
        assertTrue(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
    }

    @Test
    fun `cancel closes the review, touches no file and keeps the plan`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))

        controller.onCancel()

        assertEquals(null, controller.review.view.value)
        assertEquals(setOf("IMG_20180101_000001.jpg"), namesIn(camera))
        assertEquals(emptySet<String>(), namesIn(output.resolve("Trash")))
        controller.onApply(PathHandle(output))
        assertTrue(controller.review.view.value != null)
    }

    @Test
    fun `continue with the input file unticked sends it to the Trash and keeps the library file`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))
        controller.review.toggleInput(0)

        controller.onContinue(PathHandle(output))

        assertEquals(setOf("IMG_20180101_000001.jpg"), namesIn(output.resolve("Trash/2018/Reorg")))
        assertEquals(emptySet<String>(), namesIn(camera))
        assertEquals(setOf("IMG_20180101_000000.jpg"), namesIn(output.resolve("2018")))
        assertTrue(controller.state.appliedSuffix.contains("APPLIED (1)"))
        assertTrue(controller.state.lines.first().contains("trashed=1"))
        assertEquals(null, controller.review.view.value)
        assertFalse(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `continue with the library file unticked trashes it and keeps the input file`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))
        controller.review.toggleLibrary(0)

        controller.onContinue(PathHandle(output))

        assertEquals(setOf("IMG_20180101_000000.jpg"), namesIn(output.resolve("Trash/2018/Reorg")))
        assertEquals(setOf("IMG_20180101_000001.jpg"), namesIn(camera))
    }

    @Test
    fun `continue with no review open does nothing`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))

        controller.onContinue(PathHandle(output))

        assertEquals(0, applied)
        assertEquals(setOf("IMG_20180101_000001.jpg"), namesIn(camera))
    }

    @Test
    fun `a new scan closes an open review`() {
        duplicateInLibrary()
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))

        controller.onScan(PathHandle(output))

        assertEquals(null, controller.review.view.value)
    }

    private fun waitUntilIdle(jobs: JobQueue) {
        runBlocking {
            withTimeout(WAIT_SECONDS * MILLIS) {
                jobs.entries.first { entries -> entries.all { it.state is JobState.Finished } }
            }
        }
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val MILLIS = 1000L
    }
}
