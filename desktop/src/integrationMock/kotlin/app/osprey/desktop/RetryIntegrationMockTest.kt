package app.osprey.desktop

import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.RetryPlanner
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.history.openHistory
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retry failed (#83) through the real controllers, a real job queue, real files and a real history on disk: a run that
 * failed on a locked file is read back after a restart and retried by a new launch, the way the History screen does.
 */
class RetryIntegrationMockTest : TimedTest() {

    private val harness = ActionHarness()
    private val label get() = harness.libraryLabel

    @After
    fun tearDown() = harness.delete()

    private fun flatten(launch: ActionHarness.Launch, fs: FileSystemRepository = harness.fs, library: String = label) =
        flattenController(fs, launch.jobs) { library }

    /** A flatten of two nested files, the second of which is locked; returns the run as a new launch reads it. */
    private fun lockedFlatten(): RunRecord {
        harness.file("sub/a.jpg")
        harness.file("sub/b.jpg")
        val first = harness.launch()
        first.scanAndApply(flatten(first, FailsToMove(harness.fs, "b.jpg")))
        return harness.runsAfterRestart().single { it.action == "Flatten 2021 apply" }
    }

    @Test
    fun `a run that failed on a locked file is read back with its origin and can be retried`() {
        val run = lockedFlatten()

        assertEquals(RunOrigin("flatten", label, "2021"), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals(listOf("2021/sub/b.jpg"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
        assertTrue(RetryPlanner.canRetry(run, label, "2021"))
    }

    @Test
    fun `a retry after a restart moves the file that failed, links its run and leaves the first run as it was`() {
        val run = lockedFlatten()
        val second = harness.launch()

        flatten(second).onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        assertTrue(Files.exists(harness.year.resolve("b.jpg")))
        assertTrue(Files.notExists(harness.year.resolve("sub")))
        val runs = harness.runsAfterRestart()
        val retry = runs.single { it.action == "Flatten 2021 retry" }
        assertEquals(RunOrigin("flatten", label, "2021", retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
        assertTrue(retry.summary, retry.summary.startsWith("Retry of run #${run.id}: "))
        assertEquals(run, runs.single { it.id == run.id })
    }

    @Test
    fun `a retry leaves alone a file that was added after the failed run`() {
        val run = lockedFlatten()
        harness.file("sub/c.jpg")
        val second = harness.launch()

        flatten(second).onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        assertTrue(Files.exists(harness.year.resolve("b.jpg")))
        assertTrue(Files.exists(harness.year.resolve("sub").resolve("c.jpg")))
        assertEquals(
            listOf("2021/sub/b.jpg"),
            harness.runsAfterRestart().single { it.action == "Flatten 2021 retry" }.files.map { it.path },
        )
    }

    @Test
    fun `a failed file that is gone by the retry is reported skipped and nothing fails`() {
        val run = lockedFlatten()
        Files.delete(harness.year.resolve("sub").resolve("b.jpg"))
        val second = harness.launch()

        flatten(second).onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        val retry = harness.runsAfterRestart().single { it.action == "Flatten 2021 retry" }
        assertEquals(
            listOf(FileRecord("2021/sub/b.jpg", FileResult.Skipped, "no longer there or already handled")),
            retry.files,
        )
        assertEquals(RunState.Done, retry.state)
    }

    @Test
    fun `a run written before runs recorded their origin cannot be retried`() {
        harness.file("sub/b.jpg")
        val at = Instant.parse("2026-11-01T10:00:00Z")
        val old = openHistory(harness.historyDir)
        val id = old.begin("Flatten 2021 apply", at)
        old.appendFile(id, FileRecord("2021/sub/b.jpg", FileResult.Failed, "locked"))
        old.finish(id, RunState.Failed, "Flatten 2021 apply: 0 done, 1 failed", at)
        val run = harness.runsAfterRestart().single()
        val second = harness.launch()

        flatten(second).onRetry(harness.yearHandle(), run, "2021")

        assertNull(run.origin)
        assertFalse(RetryPlanner.canRetry(run, label, "2021"))
        assertTrue(second.jobs.entries.value.isEmpty())
        assertTrue(Files.exists(harness.year.resolve("sub").resolve("b.jpg")))
    }

    @Test
    fun `a retry on another library starts nothing`() {
        val run = lockedFlatten()
        val second = harness.launch()

        flatten(second, library = "D:/Elsewhere").onRetry(harness.yearHandle(), run, "2021")

        assertTrue(second.jobs.entries.value.isEmpty())
        assertTrue(Files.exists(harness.year.resolve("sub").resolve("b.jpg")))
    }

    @Test
    fun `a Trash delete that failed on a locked file asks before it is retried, then deletes it`() {
        val folder = Files.createDirectories(harness.library.resolve("Trash/2021/Dedupe"))
        Files.writeString(folder.resolve("a.jpg"), "a")
        Files.writeString(folder.resolve("b.jpg"), "b")
        val root = PathHandle(harness.library)
        val first = harness.launch()
        val trash = TrashController(FailsToRemove(harness.fs, "a.jpg"), first.jobs, { label })
        trash.refresh(root)
        trash.toggleTab()
        trash.requestDelete()
        trash.confirmDelete(root)
        first.awaitJobs()
        val run = harness.runsAfterRestart().single { it.action == "Trash delete" }
        val second = harness.launch()
        val retry = TrashRetryController(
            harness.fs,
            second.jobs,
            TrashController(harness.fs, second.jobs, { label }),
        ) { label }

        val question = retry.question(root, run)
        retry.retry(root, run)
        second.awaitJobs()

        assertEquals(RunOrigin("trash-delete", label), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertTrue(question, question!!.startsWith("Permanently delete 1 file ("))
        assertTrue(Files.notExists(folder.resolve("a.jpg")))
        assertTrue(Files.notExists(folder.resolve("b.jpg")))
        val retried = harness.runsAfterRestart().single { it.action == "Trash delete retry" }
        assertEquals(RunOrigin("trash-delete", label, retryOf = run.id), retried.origin)
        assertEquals(RunState.Done, retried.state)
    }

    @Test
    fun `a Trash restore retried after its name was taken leaves the file in the Trash and reports it skipped`() {
        val folder = Files.createDirectories(harness.library.resolve("Trash/2021/Dedupe"))
        Files.writeString(folder.resolve("a.jpg"), "a")
        Files.writeString(folder.resolve("b.jpg"), "b")
        val root = PathHandle(harness.library)
        val first = harness.launch()
        val trash = TrashController(FailsToMove(harness.fs, "a.jpg"), first.jobs, { label })
        trash.refresh(root)
        trash.toggleTab()
        trash.restore(root)
        first.awaitJobs()
        val run = harness.runsAfterRestart().single { it.action == "Trash restore" }
        Files.writeString(harness.year.resolve("a.jpg"), "taken meanwhile")
        val second = harness.launch()
        val retry = TrashRetryController(
            harness.fs,
            second.jobs,
            TrashController(harness.fs, second.jobs, { label }),
        ) { label }

        retry.retry(root, run)
        second.awaitJobs()

        assertEquals(RunOrigin("trash-restore", label), run.origin)
        assertEquals("taken meanwhile", Files.readString(harness.year.resolve("a.jpg")))
        assertTrue(Files.exists(folder.resolve("a.jpg")))
        assertTrue(Files.exists(harness.year.resolve("b.jpg")))
        val retried = harness.runsAfterRestart().single { it.action == "Trash restore retry" }
        val taken = "name taken in the library: restore it from the Trash screen"
        assertEquals(listOf(FileRecord("Trash/2021/Dedupe/a.jpg", FileResult.Skipped, taken)), retried.files)
    }

    @Test
    fun `a Trash restore of two same-name files that both failed is retried with the second suffixed`() {
        listOf("Dedupe" to "dedupe", "VisualDupes" to "visual").forEach { (action, text) ->
            val folder = Files.createDirectories(harness.library.resolve("Trash/2021/$action"))
            Files.writeString(folder.resolve("IMG_001.jpg"), text)
        }
        val root = PathHandle(harness.library)
        val first = harness.launch()
        val trash = TrashController(FailsToMove(harness.fs, "IMG_001.jpg"), first.jobs, { label })
        trash.refresh(root)
        trash.contents.entries.forEach(trash::toggle)
        trash.restore(root)
        first.awaitJobs()
        val run = harness.runsAfterRestart().single { it.action == "Trash restore" }
        assertEquals(RunState.Failed, run.state)
        val second = harness.launch()
        val inner = TrashController(harness.fs, second.jobs, { label })
        val retry = TrashRetryController(harness.fs, second.jobs, inner) { label }

        retry.retry(root, run)
        second.awaitJobs()

        assertEquals("dedupe", Files.readString(harness.year.resolve("IMG_001.jpg")))
        assertEquals("visual", Files.readString(harness.year.resolve("IMG_001_VisualDupes.jpg")))
        val retried = harness.runsAfterRestart().single { it.action == "Trash restore retry" }
        assertEquals(
            listOf(
                FileRecord("Trash/2021/Dedupe/IMG_001.jpg", FileResult.Done, "restored"),
                FileRecord(
                    "Trash/2021/VisualDupes/IMG_001.jpg",
                    FileResult.Done,
                    "restored as IMG_001_VisualDupes.jpg",
                ),
            ),
            retried.files,
        )
    }
}
