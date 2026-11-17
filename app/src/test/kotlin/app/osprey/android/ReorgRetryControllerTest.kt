package app.osprey.android

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.InputMode
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LIBRARY = "content://library"
private const val LOCKED = "IMG_20180101_000000.jpg"
private const val OTHER = "IMG_20180102_000000.jpg"
private const val PAIR = "IMG_20180105_000000.jpg"

/** The retry of the failed files of a Reorg run (#83), with one copy made to fail. */
class ReorgRetryControllerTest {

    private val history = InMemoryJobHistoryStore()
    private val fake = FakeFileSystemRepository()
    private val fs = FaultyFileSystemRepository(fake)
    private val output = fake.addFolder(fake.root, "Library")
    private val camera = fake.addFolder(fake.root, "Camera")
    private val sources = listOf(ReorgSource(camera, "Camera", InputMode.MOVE))
    private var library = LIBRARY
    private lateinit var locked: FileHandle
    private val controller = ReorgController(
        ReorgPlanner(fs, FakeMediaMetadataReader()), immediateJobQueue(history), { sources }, { library },
    )

    @Before
    fun setUp() {
        locked = fake.addFile(camera, LOCKED, "locked".toByteArray())
        fs.failCopy = { if (it == locked) IOException("locked") else null }
    }

    private fun names(folder: FileHandle) = fake.list(folder).map { it.name }.toSet()

    private fun year(name: String) = fake.list(output).first { it.name == name }.handle

    private fun failedRun(): RunRecord {
        controller.onScan(output)
        controller.onApply(output)
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

        controller.onRetry(output, run)

        assertEquals(emptySet<String>(), names(camera))
        assertEquals(setOf(LOCKED), names(year("2018")))
        val retry = retries().single()
        assertEquals(RunOrigin("reorg", LIBRARY, retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
        assertTrue(retry.summary, retry.summary.startsWith("Retry of run #${run.id}: "))
    }

    @Test
    fun `a retry leaves alone a file that was not part of the failed run`() {
        val run = failedRun()
        fake.addFile(camera, OTHER, "new".toByteArray())
        fs.failCopy = { null }

        controller.onRetry(output, run)

        assertEquals(setOf(OTHER), names(camera))
        assertEquals(setOf(LOCKED), names(year("2018")))
    }

    @Test
    fun `a failed file that is gone is reported skipped, not fatal`() {
        val run = failedRun()
        fake.delete(locked)

        controller.onRetry(output, run)

        assertEquals(
            listOf(FileRecord("Camera/$LOCKED", FileResult.Skipped, "no longer there or already handled")),
            retries().single().files,
        )
    }

    @Test
    fun `a run that cannot be retried, or another library, starts nothing`() {
        val run = failedRun()

        controller.onRetry(output, run.copy(state = RunState.Done))
        library = "content://elsewhere"
        controller.onRetry(output, run)

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a retry does nothing while the controller is busy`() {
        val run = failedRun()
        controller.state.busy = true

        controller.onRetry(output, run)

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a pair whose input side failed to go to the Trash is trashed again by the retry`() {
        fake.delete(locked)
        val twin = fake.addFile(fake.addFolder(output, "2018"), PAIR, "same bytes".toByteArray())
        val input = fake.addFile(camera, PAIR, "same bytes".toByteArray())
        fs.failCopy = { if (it == input) IOException("locked") else null }
        controller.onScan(output)
        controller.onApply(output)
        controller.review.toggleInput(0)
        controller.onContinue(output)
        val run = history.runs().first { it.action == "Reorg apply" }
        fs.failCopy = { null }

        controller.onRetry(output, run)

        assertEquals(listOf("Camera/$PAIR"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
        assertEquals(emptySet<String>(), names(camera))
        assertTrue(fake.list(year("2018")).any { it.handle == twin })
        val trash = fake.list(year("Trash")).first { it.name == "2018" }.handle
        assertEquals(setOf(PAIR), names(fake.list(trash).first { it.name == "Reorg" }.handle))
        assertEquals(RunState.Done, retries().single().state)
    }
}
