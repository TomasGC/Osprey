package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.settings.InputMode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LIBRARY = "D:/Photos"
private const val LOCKED = "IMG_20180101_000000.jpg"
private const val OTHER = "IMG_20180102_000000.jpg"
private const val PAIR = "IMG_20180105_000000.jpg"
private const val MISFILED = "IMG_20180106_000000.jpg"

/** The retry of the failed files of a Reorg run (#83), on real files with one copy made to fail. */
class ReorgRetryControllerTest : TimedTest() {

    private object NameOnlyReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? = null
    }

    private lateinit var tempDir: Path
    private lateinit var output: Path
    private lateinit var camera: Path
    private lateinit var controller: ReorgController
    private val history = InMemoryJobHistoryStore()
    private val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
    private var library = LIBRARY

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-reorg-retry-test")
        output = Files.createDirectory(tempDir.resolve("Library"))
        camera = Files.createDirectory(tempDir.resolve("Camera"))
        Files.writeString(camera.resolve(LOCKED), "locked")
        fs.failCopy = { if ((it as PathHandle).path.fileName.toString() == LOCKED) IOException("locked") else null }
        val sources = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))
        controller = ReorgController(
            ReorgPlanner(fs, NameOnlyReader), immediateJobQueue(history), { sources }, { library },
        )
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun failedRun(): RunRecord {
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))
        return history.runs().first { it.action == "Reorg apply" }
    }

    private fun retries() = history.runs().filter { it.action == "Reorg retry" }

    @Test
    fun `an apply run is recorded with the library and no year, and fails on the locked file`() {
        val run = failedRun()

        assertEquals(RunOrigin("reorg", LIBRARY), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals("Camera/$LOCKED", run.files.single { it.result == FileResult.Failed }.path)
    }

    @Test
    fun `a retry moves the file that failed and records a run linked to the first`() {
        val run = failedRun()
        fs.failCopy = { null }

        controller.onRetry(PathHandle(output), run)

        assertTrue(Files.notExists(camera.resolve(LOCKED)))
        assertTrue(Files.exists(output.resolve("2018").resolve(LOCKED)))
        val retry = retries().single()
        assertEquals(RunOrigin("reorg", LIBRARY, retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
        assertTrue(retry.summary, retry.summary.startsWith("Retry of run #${run.id}: "))
    }

    @Test
    fun `a retry leaves alone a file that was not part of the failed run`() {
        val run = failedRun()
        Files.writeString(camera.resolve(OTHER), "new")
        fs.failCopy = { null }

        controller.onRetry(PathHandle(output), run)

        assertTrue(Files.exists(camera.resolve(OTHER)))
        assertTrue(Files.exists(output.resolve("2018").resolve(LOCKED)))
    }

    @Test
    fun `a failed file that is gone is reported skipped, not fatal`() {
        val run = failedRun()
        Files.delete(camera.resolve(LOCKED))

        controller.onRetry(PathHandle(output), run)

        assertEquals(
            listOf(FileRecord("Camera/$LOCKED", FileResult.Skipped, "no longer there or already handled")),
            retries().single().files,
        )
    }

    @Test
    fun `a run that cannot be retried, or another library, starts nothing`() {
        val run = failedRun()

        controller.onRetry(PathHandle(output), run.copy(state = RunState.Done))
        library = "E:/Elsewhere"
        controller.onRetry(PathHandle(output), run)

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a retry does nothing while the controller is busy`() {
        val run = failedRun()
        controller.state.busy = true

        controller.onRetry(PathHandle(output), run)

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a pair whose input side failed to go to the Trash is trashed again by the retry`() {
        Files.delete(camera.resolve(LOCKED))
        Files.createDirectory(output.resolve("2018"))
        Files.writeString(output.resolve("2018").resolve(PAIR), "same bytes")
        Files.writeString(camera.resolve(PAIR), "same bytes")
        fs.failCopy = {
            if ((it as PathHandle).path.parent.fileName.toString() == "Camera") IOException("locked") else null
        }
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))
        controller.review.toggleInput(0)
        controller.onContinue(PathHandle(output))
        val run = history.runs().first { it.action == "Reorg apply" }
        fs.failCopy = { null }

        controller.onRetry(PathHandle(output), run)

        assertEquals(listOf("Camera/$PAIR"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
        assertTrue(Files.notExists(camera.resolve(PAIR)))
        assertTrue(Files.exists(output.resolve("2018").resolve(PAIR)))
        assertTrue(Files.exists(output.resolve("Trash/2018/Reorg").resolve(PAIR)))
        assertEquals(RunState.Done, retries().single().state)
    }

    @Test
    fun `a library file whose refile was dropped and whose trash failed is trashed again by the retry`() {
        Files.delete(camera.resolve(LOCKED))
        Files.createDirectory(output.resolve("2019"))
        Files.writeString(output.resolve("2019").resolve(MISFILED), "same bytes")
        Files.writeString(camera.resolve(MISFILED), "same bytes")
        fs.failMove = {
            if ((it as PathHandle).path.parent.fileName.toString() == "2019") IOException("locked") else null
        }
        controller.onScan(PathHandle(output))
        controller.onApply(PathHandle(output))
        controller.review.toggleLibrary(0)
        controller.onContinue(PathHandle(output))
        val run = history.runs().first { it.action == "Reorg apply" }
        fs.failMove = { null }

        controller.onRetry(PathHandle(output), run)

        assertEquals(listOf("2019/$MISFILED"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
        assertTrue(Files.notExists(output.resolve("2019").resolve(MISFILED)))
        assertTrue(Files.exists(output.resolve("Trash/2019/Reorg").resolve(MISFILED)))
        assertEquals(RunState.Done, retries().single().state)
    }
}
