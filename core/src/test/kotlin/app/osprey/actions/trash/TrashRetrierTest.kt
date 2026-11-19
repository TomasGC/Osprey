package app.osprey.actions.trash

import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.RecordingJobControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GONE = "no longer there or already handled"
private const val TAKEN = "name taken in the library: restore it from the Trash screen"

class TrashRetrierTest {

    private val lib = TrashTestLibrary()
    private val retrier = TrashRetrier(lib.fs)
    private val control = RecordingJobControl()

    @Test
    fun `the entries of a retry are the Trash files whose label failed`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.DEDUPE, "b.jpg")
        val c = lib.trash("2020", TrashAction.VISUAL_DUPES, "c.jpg")

        assertEquals(listOf(a, c), retrier.entries(lib.root, setOf(a.label, c.label, "Trash/2019/Dedupe/gone.jpg")))
    }

    @Test
    fun `the question names only the failed files and is null when none is left`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg", ByteArray(2_048))
        lib.trash("2019", TrashAction.DEDUPE, "b.jpg")

        assertEquals(clearConfirmation(listOf(a)), retrier.deleteQuestion(lib.root, setOf(a.label)))
        assertNull(retrier.deleteQuestion(lib.root, setOf("Trash/2019/Dedupe/gone.jpg")))
    }

    @Test
    fun `a delete retry deletes the failed files only and skips the ones that are gone`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.DEDUPE, "b.jpg")

        val result = retrier.delete(lib.root, setOf(a.label, "Trash/2019/Dedupe/gone.jpg"), control)

        assertEquals(1, result.deleted)
        assertEquals(setOf("b.jpg"), lib.namesIn("Trash", "2019", "Dedupe"))
        assertEquals(
            listOf(
                FileRecord("Trash/2019/Dedupe/gone.jpg", FileResult.Skipped, GONE),
                FileRecord(a.label, FileResult.Done, "deleted"),
            ),
            control.files,
        )
    }

    @Test
    fun `a restore retry restores the failed files whose name is free and skips the ones that are gone`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2019", TrashAction.DEDUPE, "b.jpg")

        val result = retrier.restore(lib.root, setOf(a.label, "Trash/2019/Dedupe/gone.jpg"), control)

        assertEquals(1, result.restored)
        assertEquals(setOf("a.jpg"), lib.namesIn("2019"))
        assertEquals(setOf("b.jpg"), lib.namesIn("Trash", "2019", "Dedupe"))
        assertTrue(control.files.contains(FileRecord("Trash/2019/Dedupe/gone.jpg", FileResult.Skipped, GONE)))
        assertTrue(control.files.contains(FileRecord(a.label, FileResult.Done, "restored")))
    }

    @Test
    fun `a restore retry leaves a file whose name is now taken in the Trash and reports it skipped`() {
        val a = lib.trash("2019", TrashAction.DEDUPE, "a.jpg")
        lib.library("2019", "a.jpg", "other".toByteArray())

        val result = retrier.restore(lib.root, setOf(a.label), control)

        assertEquals(0, result.restored)
        assertEquals(setOf("a.jpg"), lib.namesIn("Trash", "2019", "Dedupe"))
        assertEquals(listOf(FileRecord(a.label, FileResult.Skipped, TAKEN)), control.files)
    }

    @Test
    fun `a restore retry of two same-name files that both failed restores both, the second suffixed`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg", "dedupe".toByteArray())
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg", "visual".toByteArray())

        val result = retrier.restore(lib.root, setOf(dedupe.label, visual.label), control)

        assertEquals(2, result.restored)
        assertTrue(result.failures.isEmpty())
        assertEquals(setOf("a.jpg", "a_VisualDupes.jpg"), lib.namesIn("2021"))
        assertTrue(control.files.contains(FileRecord(visual.label, FileResult.Done, "restored as a_VisualDupes.jpg")))
    }

    @Test
    fun `a restore retry of the plain file alone restores it under its own name`() {
        val dedupe = lib.trash("2021", TrashAction.DEDUPE, "a.jpg")
        lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg")
        lib.library("2021", "a_VisualDupes.jpg")

        val result = retrier.restore(lib.root, setOf(dedupe.label), control)

        assertEquals(1, result.restored)
        assertEquals(setOf("a.jpg", "a_VisualDupes.jpg"), lib.namesIn("2021"))
        assertEquals(setOf("a.jpg"), lib.namesIn("Trash", "2021", "VisualDupes"))
    }

    @Test
    fun `a restore retry of the suffixed file alone finds the plain name taken and reports it skipped`() {
        lib.library("2021", "a.jpg", "restored first".toByteArray())
        val visual = lib.trash("2021", TrashAction.VISUAL_DUPES, "a.jpg", "visual".toByteArray())

        val result = retrier.restore(lib.root, setOf(visual.label), control)

        assertEquals(0, result.restored)
        assertEquals(setOf("a.jpg"), lib.namesIn("Trash", "2021", "VisualDupes"))
        assertEquals(listOf(FileRecord(visual.label, FileResult.Skipped, TAKEN)), control.files)
    }

    @Test
    fun `a clear outcome counts what was deleted and what failed, and notes a stop`() {
        val result = TrashClearResult(
            2,
            listOf(TrashFailure("Trash/2019/Dedupe/a.jpg", "locked")),
            1,
            cancelled = false,
        )

        val outcome = clearOutcome(result)

        assertEquals("Trash: 2 deleted, 1 failed", outcome.summary)
        assertEquals(clearResultLines(result), outcome.lines)
        assertEquals(1, outcome.failures)
        assertEquals(
            "Trash: 0 deleted, 0 failed (stopped)",
            clearOutcome(TrashClearResult(0, emptyList(), 0, true)).summary,
        )
        assertTrue(clearOutcome(TrashClearResult(0, emptyList(), 0, true)).stopped)
    }

    @Test
    fun `a restore outcome counts what was restored and what failed, and notes a stop`() {
        val result = TrashRestoreResult(
            3,
            listOf(TrashFailure("Trash/2019/Dedupe/a.jpg", "locked")),
            0,
            cancelled = false,
        )

        val outcome = restoreOutcome(result, conflicts = 2)

        assertEquals("Trash: 3 restored, 1 failed", outcome.summary)
        assertEquals(restoreResultLines(result, 2), outcome.lines)
        assertEquals(1, outcome.failures)
        assertEquals(
            "Trash: 0 restored, 0 failed (stopped)",
            restoreOutcome(TrashRestoreResult(0, emptyList(), 0, true), 0).summary,
        )
    }
}
