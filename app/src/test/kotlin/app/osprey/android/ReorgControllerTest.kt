package app.osprey.android

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.settings.InputMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReorgControllerTest {

    private val fs = FakeFileSystemRepository()
    private val output = fs.addFolder(fs.root, "Library")
    private val camera = fs.addFolder(fs.root, "Camera")
    private val sources = listOf(ReorgSource(camera, "Camera", InputMode.MOVE))
    private lateinit var controller: ReorgController
    private var applied = 0

    @Before
    fun setUp() {
        controller = controllerOn(immediateJobQueue())
    }

    private fun controllerOn(jobs: JobQueue) =
        ReorgController(ReorgPlanner(fs, FakeMediaMetadataReader()), jobs, { sources }) { applied++ }

    private fun names(folder: FileHandle) = fs.list(folder).map { it.name }.toSet()

    @Test
    fun `apply before any scan does nothing`() {
        fs.addFile(camera, "IMG_20180101_000000.jpg")

        controller.onApply(output)

        assertEquals(setOf("IMG_20180101_000000.jpg"), names(camera))
        assertFalse(controller.state.applyEnabled)
        assertEquals(0, applied)
    }

    @Test
    fun `scan lists the plan and enables apply`() {
        fs.addFile(camera, "IMG_20180101_000000.jpg")

        controller.onScan(output)

        assertTrue(controller.state.applyEnabled)
        assertTrue(controller.state.lines.any { it.contains("MOVE Camera/IMG_20180101_000000.jpg -> 2018/") })
        assertFalse(controller.state.busy)
    }

    @Test
    fun `scan with nothing to do leaves apply disabled`() {
        controller.onScan(output)

        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `a scan that is stopped keeps no plan and says so`() {
        fs.addFile(camera, "IMG_20180101_000000.jpg")
        fs.addFile(camera, "IMG_20180102_000000.jpg")
        val jobs = immediateJobQueue()
        val pressingStop = object : MediaMetadataReader {
            override fun captureDateTime(entry: FileHandle): MediaDate? {
                jobs.stop(jobs.entries.value.last().id)
                return null
            }

            override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? = null
        }
        val stopping = ReorgController(ReorgPlanner(fs, pressingStop), jobs, { sources })

        stopping.onScan(output)

        assertEquals("Scan stopped", stopping.state.summary)
        assertFalse(stopping.state.applyEnabled)
    }

    @Test
    fun `apply moves the files, reports the result, consumes the plan and tells the app`() {
        fs.addFile(camera, "IMG_20180101_000000.jpg")
        controller.onScan(output)

        controller.onApply(output)

        val year = fs.list(output).single { it.name == "2018" }.handle
        assertEquals(setOf("IMG_20180101_000000.jpg"), names(year))
        assertEquals(emptySet<String>(), names(camera))
        assertTrue(controller.state.appliedSuffix.contains("APPLIED (1)"))
        assertTrue(controller.state.lines.first().contains("APPLIED copied=0 moved=1"))
        assertFalse(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
        assertEquals(1, applied)
    }

    @Test
    fun `an apply stopped while it still waits leaves the files and frees the buttons`() {
        fs.addFile(camera, "IMG_20180101_000000.jpg")
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val gate = CountDownLatch(1)
        try {
            val onRealQueue = controllerOn(jobs)
            onRealQueue.onScan(output)
            waitUntilIdle(jobs)
            jobs.enqueue("Blocker") {
                gate.await(WAIT_SECONDS, TimeUnit.SECONDS)
                JobOutcome("done")
            }

            onRealQueue.onApply(output)
            jobs.stop(jobs.entries.value.single { it.name == "Reorg apply" }.id)
            gate.countDown()
            waitUntilIdle(jobs)

            assertEquals(setOf("IMG_20180101_000000.jpg"), names(camera))
            assertFalse(onRealQueue.state.busy)
            assertEquals(1, applied)
        } finally {
            gate.countDown()
        }
    }

    private fun folderAt(vararg path: String): FileHandle? {
        var current: FileHandle = output
        for (segment in path) {
            current = fs.list(current).firstOrNull { it.isDirectory && it.name == segment }?.handle ?: return null
        }
        return current
    }

    private fun namesAt(vararg path: String): Set<String> = folderAt(*path)?.let(::names).orEmpty()

    /** The library holds 2018/IMG_20180101_000000.jpg; the Move input holds the same bytes under another name. */
    private fun duplicateInLibrary() {
        fs.addFile(fs.addFolder(output, "2018"), "IMG_20180101_000000.jpg", "a".toByteArray())
        fs.addFile(camera, "IMG_20180101_000001.jpg", "a".toByteArray())
    }

    @Test
    fun `scan lists a Move file already in the library as a duplicate and enables apply`() {
        duplicateInLibrary()

        controller.onScan(output)

        assertTrue(controller.state.applyEnabled)
        assertTrue(controller.state.lines.any { it.contains("DUPLICATE Camera/IMG_20180101_000001.jpg == 2018/") })
    }

    @Test
    fun `apply with a duplicate opens the review and moves nothing`() {
        duplicateInLibrary()
        controller.onScan(output)

        controller.onApply(output)

        assertTrue(controller.review.view.value != null)
        assertEquals(setOf("IMG_20180101_000001.jpg"), names(camera))
        assertEquals(0, applied)
        assertTrue(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
    }

    @Test
    fun `cancel closes the review, touches no file and keeps the plan`() {
        duplicateInLibrary()
        controller.onScan(output)
        controller.onApply(output)

        controller.onCancel()

        assertEquals(null, controller.review.view.value)
        assertEquals(setOf("IMG_20180101_000001.jpg"), names(camera))
        assertEquals(emptySet<String>(), namesAt("Trash"))
        controller.onApply(output)
        assertTrue(controller.review.view.value != null)
    }

    @Test
    fun `continue with the input file unticked sends it to the Trash and keeps the library file`() {
        duplicateInLibrary()
        controller.onScan(output)
        controller.onApply(output)
        controller.review.toggleInput(0)

        controller.onContinue(output)

        assertEquals(setOf("IMG_20180101_000001.jpg"), namesAt("Trash", "2018", "Reorg"))
        assertEquals(emptySet<String>(), names(camera))
        assertEquals(setOf("IMG_20180101_000000.jpg"), namesAt("2018"))
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
        controller.onScan(output)
        controller.onApply(output)
        controller.review.toggleLibrary(0)

        controller.onContinue(output)

        assertEquals(setOf("IMG_20180101_000000.jpg"), namesAt("Trash", "2018", "Reorg"))
        assertEquals(setOf("IMG_20180101_000001.jpg"), names(camera))
    }

    @Test
    fun `continue with no review open does nothing`() {
        duplicateInLibrary()
        controller.onScan(output)

        controller.onContinue(output)

        assertEquals(0, applied)
        assertEquals(setOf("IMG_20180101_000001.jpg"), names(camera))
    }

    @Test
    fun `a new scan closes an open review`() {
        duplicateInLibrary()
        controller.onScan(output)
        controller.onApply(output)

        controller.onScan(output)

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
