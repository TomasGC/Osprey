package app.osprey.jobs

import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderJobControlTest {

    private val inner = RecordingJobControl()

    @Test
    fun `a file is recorded under the folder`() {
        inner.underFolder("2021").record(FileRecord("sub/a.jpg", FileResult.Done, "moved up"))

        assertEquals(listOf(FileRecord("2021/sub/a.jpg", FileResult.Done, "moved up")), inner.files)
    }

    @Test
    fun `everything else about the file is kept`() {
        val file = FileRecord("a.jpg", FileResult.Failed, "locked")

        inner.underFolder("2021").record(file)

        assertEquals(file.copy(path = "2021/a.jpg"), inner.files.single())
    }

    @Test
    fun `without a folder, or with a blank one, the control is the same one`() {
        assertSame(inner, inner.underFolder(null))
        assertSame(inner, inner.underFolder(" "))
        assertSame(inner, inner.underFolder(""))
    }

    @Test
    fun `stopping, progress and the failed count go through to the control underneath`() {
        val stopping = RecordingJobControl(stopAfterReports = 1)
        val placed = stopping.underFolder("2021")
        assertFalse(placed.shouldStop())

        placed.report(0, 5, "a.jpg")
        placed.record(FileRecord("a.jpg", FileResult.Failed, "locked"))

        assertTrue(placed.shouldStop())
        assertEquals(listOf(JobProgress(0, 5, "a.jpg")), stopping.reports)
        assertEquals(1, placed.failedFiles)
    }
}
