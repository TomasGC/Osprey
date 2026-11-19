package app.osprey.desktop

import app.osprey.actions.trash.clearConfirmation
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LIBRARY = "D:/Photos"
private const val LABEL = "Trash/2019/Dedupe/a.jpg"

/** Retrying the failed files of a Trash delete or restore (#83), on real files with one of them locked. */
class TrashRetryControllerTest : TimedTest() {

    private lateinit var library: Path
    private val history = InMemoryJobHistoryStore()
    private val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
    private val jobs = immediateJobQueue(history)
    private var label = LIBRARY
    private val trash = TrashController(fs, jobs, { label })
    private var changed = 0
    private val retry = TrashRetryController(fs, jobs, trash, { changed++ }) { label }

    private val root get() = PathHandle(library)
    private fun trashed(name: String) = library.resolve("Trash/2019/Dedupe").resolve(name)

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-trash-retry-test")
        Files.createDirectories(trashed("a.jpg").parent)
        Files.writeString(trashed("a.jpg"), "a")
        Files.writeString(trashed("b.jpg"), "b")
        val locked = { handle: Any? ->
            if ((handle as PathHandle).path.fileName.toString() == "a.jpg") IOException("locked") else null
        }
        fs.failDelete = locked
        fs.failMove = locked
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun failedDelete(): RunRecord {
        trash.refresh(root)
        trash.toggleTab()
        trash.requestDelete()
        trash.confirmDelete(root)
        return history.runs().first { it.action == "Trash delete" }
    }

    private fun failedRestore(): RunRecord {
        trash.refresh(root)
        trash.toggleTab()
        trash.restore(root)
        return history.runs().first { it.action == "Trash restore" }
    }

    private fun retries(name: String) = history.runs().filter { it.action == name }

    @Test
    fun `a delete is recorded with its kind and library, and fails on the locked file`() {
        val run = failedDelete()

        assertEquals(RunOrigin("trash-delete", LIBRARY), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals(listOf(LABEL), run.files.filter { it.result == FileResult.Failed }.map { it.path })
    }

    @Test
    fun `a restore is recorded with its kind and library`() {
        assertEquals(RunOrigin("trash-restore", LIBRARY), failedRestore().origin)
    }

    @Test
    fun `retrying a failed delete asks to delete that file again, and nothing else`() {
        val run = failedDelete()

        val question = retry.question(root, run)

        assertEquals(clearConfirmation(trash.contents.entries.filter { it.name == "a.jpg" }), question)
        assertTrue(question, question!!.startsWith("Permanently delete 1 file ("))
    }

    @Test
    fun `a failed restore asks nothing`() {
        assertNull(retry.question(root, failedRestore()))
    }

    @Test
    fun `a run that cannot be retried asks nothing`() {
        assertNull(retry.question(root, failedDelete().copy(state = RunState.Done)))
    }

    @Test
    fun `a failed delete whose file is gone asks nothing`() {
        val run = failedDelete()
        fs.failDelete = { null }
        Files.delete(trashed("a.jpg"))

        assertNull(retry.question(root, run))
    }

    @Test
    fun `retrying a delete removes the file that failed and records a run linked to the first`() {
        val run = failedDelete()
        fs.failDelete = { null }

        retry.retry(root, run)

        assertTrue(Files.notExists(trashed("a.jpg")))
        val retried = retries("Trash delete retry").single()
        assertEquals(1, changed)
        assertTrue(trash.contents.entries.none { it.name == "a.jpg" })
        assertEquals(RunOrigin("trash-delete", LIBRARY, retryOf = run.id), retried.origin)
        assertEquals(RunState.Done, retried.state)
        assertTrue(retried.summary, retried.summary.startsWith("Retry of run #${run.id}: Trash: 1 deleted, 0 failed"))
    }

    @Test
    fun `retrying a restore puts the file that failed back in its year`() {
        val run = failedRestore()
        fs.failMove = { null }

        retry.retry(root, run)

        assertTrue(Files.exists(library.resolve("2019").resolve("a.jpg")))
        assertTrue(Files.notExists(trashed("a.jpg")))
        val retried = retries("Trash restore retry").single()
        assertEquals(RunOrigin("trash-restore", LIBRARY, retryOf = run.id), retried.origin)
        assertEquals(RunState.Done, retried.state)
    }

    @Test
    fun `a retry leaves alone a Trash file that was not part of the failed run`() {
        val run = failedDelete()
        Files.writeString(trashed("c.jpg"), "c")
        fs.failDelete = { null }

        retry.retry(root, run)

        assertTrue(Files.exists(trashed("c.jpg")))
    }

    @Test
    fun `a failed file that is gone is reported skipped, not fatal`() {
        val run = failedDelete()
        fs.failDelete = { null }
        Files.delete(trashed("a.jpg"))

        retry.retry(root, run)

        assertEquals(
            listOf(FileRecord(LABEL, FileResult.Skipped, "no longer there or already handled")),
            retries("Trash delete retry").single().files,
        )
    }

    @Test
    fun `a run that cannot be retried, of another library or of another kind, starts nothing`() {
        val run = failedDelete()

        retry.retry(root, run.copy(state = RunState.Done))
        label = "E:/Elsewhere"
        retry.retry(root, run)
        label = LIBRARY
        retry.retry(root, run.copy(origin = RunOrigin("dedupe", LIBRARY)))

        assertTrue(history.runs().none { it.action.endsWith("retry") })
        assertEquals(0, changed)
    }

    @Test
    fun `a retry does nothing while the Trash is busy`() {
        val run = failedDelete()
        trash.state.busy = true

        retry.retry(root, run)

        assertTrue(history.runs().none { it.action.endsWith("retry") })
    }
}
