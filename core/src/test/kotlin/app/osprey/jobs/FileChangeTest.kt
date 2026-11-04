package app.osprey.jobs

import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChangeTest {

    private val control = RecordingJobControl()
    private val first = FileState(5, name = "a.jpg")
    private val second = FileState(5, name = "b.jpg")

    @Test
    fun `a change that works is recorded as done with its before and after`() {
        val worked = control.applyToFile("a.jpg") {
            before = first
            after = second
        }

        assertTrue(worked)
        assertEquals(listOf(FileRecord("a.jpg", FileResult.Done, before = first, after = second)), control.files)
    }

    @Test
    fun `a file system failure is recorded as failed with its reason and the state it had`() {
        val worked = control.applyToFile("a.jpg") {
            before = first
            throw IOException("locked")
        }

        assertFalse(worked)
        assertEquals(listOf(FileRecord("a.jpg", FileResult.Failed, "locked", before = first)), control.files)
        assertEquals(1, control.failedFiles)
    }

    @Test
    fun `a bug is not a file failure and still propagates`() {
        assertThrows(UnsupportedOperationException::class.java) {
            control.applyToFile("a.jpg") { throw UnsupportedOperationException("bug") }
        }
        assertEquals(emptyList<FileRecord>(), control.files)
    }

    @Test
    fun `a result without a failure is recorded as done with what was done`() {
        control.recordResult("Camera/a.jpg", null, "copied to 2018/a.jpg")

        assertEquals(listOf(FileRecord("Camera/a.jpg", FileResult.Done, "copied to 2018/a.jpg")), control.files)
    }

    @Test
    fun `a result with a failure is recorded as failed with its reason`() {
        control.recordResult("Camera/a.jpg", "locked", "copied to 2018/a.jpg")

        assertEquals(listOf(FileRecord("Camera/a.jpg", FileResult.Failed, "locked")), control.files)
        assertEquals(1, control.failedFiles)
    }

    @Test
    fun `a recorded file that works is recorded as done and gives back the result`() {
        val result = control.recordingFile("a.jpg", "moved to b.jpg") { 42 }

        assertEquals(42, result)
        assertEquals(listOf(FileRecord("a.jpg", FileResult.Done, "moved to b.jpg")), control.files)
    }

    @Test
    fun `a recorded file that fails is recorded as failed and the failure goes on`() {
        val failure = IOException("locked")

        val thrown = assertThrows(IOException::class.java) {
            control.recordingFile("a.jpg", "moved to b.jpg") { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals(listOf(FileRecord("a.jpg", FileResult.Failed, "locked")), control.files)
        assertEquals(1, control.failedFiles)
    }

    @Test
    fun `a recorded file that fails without a message is recorded with the kind of failure`() {
        assertThrows(IllegalStateException::class.java) {
            control.recordingFile("a.jpg", "moved to b.jpg") { throw IllegalStateException() }
        }

        assertEquals(listOf(FileRecord("a.jpg", FileResult.Failed, "IllegalStateException")), control.files)
    }
}
