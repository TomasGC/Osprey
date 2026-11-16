package app.osprey.actions.visual

import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.RecordingJobControl
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GONE = "no longer there or already handled"

class VisualDupeRetryTest {

    private val fake = FakeFileSystemRepository()
    private val fs = FaultyFileSystemRepository(fake)
    private val year = fake.addFolder(fake.root, "2019")
    private val trash = TrashLocation(fake.root, "2019")
    private val control = RecordingJobControl()

    private fun names(folder: FileHandle) = fake.list(folder).map { it.name }.toSet()

    private fun trashed(): Set<String> {
        val folder = fake.list(fake.root).firstOrNull { it.name == "Trash" } ?: return emptySet()
        val yearFolder = fake.list(folder.handle).first { it.name == "2019" }
        val action = fake.list(yearFolder.handle).firstOrNull { it.name == "VisualDupes" } ?: return emptySet()
        return names(action.handle)
    }

    @Test
    fun `a retry moves the failed files that are still there to the Trash and touches nothing else`() {
        fake.addFile(year, "a.jpg")
        fake.addFile(year, "b.jpg")
        fake.addFile(year, "keep.jpg")

        val result = retryVisualDupes(fs, trash, year, setOf("a.jpg", "b.jpg"), control)

        assertEquals(setOf("a.jpg", "b.jpg"), result.moved.toSet())
        assertEquals(setOf("keep.jpg"), names(year))
        assertEquals(setOf("a.jpg", "b.jpg"), trashed())
    }

    @Test
    fun `a failed name that is no longer in the folder is reported skipped`() {
        fake.addFile(year, "a.jpg")

        val result = retryVisualDupes(fs, trash, year, setOf("a.jpg", "gone.jpg"), control)

        assertEquals(listOf("gone.jpg"), result.skipped)
        assertTrue(control.files.contains(FileRecord("gone.jpg", FileResult.Skipped, GONE)))
        assertEquals(setOf("a.jpg"), trashed())
    }

    @Test
    fun `a folder with the name of a failed file is not moved and counts as gone`() {
        fake.addFolder(year, "a.jpg")

        val result = retryVisualDupes(fs, trash, year, setOf("a.jpg"), control)

        assertEquals(listOf("a.jpg"), result.skipped)
        assertEquals(setOf("a.jpg"), names(year))
        assertFalse(fake.list(fake.root).any { it.name == "Trash" })
    }

    @Test
    fun `a file that fails again stays in the folder and is reported failed`() {
        val locked = fake.addFile(year, "a.jpg")
        fake.addFile(year, "b.jpg")
        fs.failMove = { if (it == locked) IOException("locked") else null }

        val result = retryVisualDupes(fs, trash, year, setOf("a.jpg", "b.jpg"), control)

        assertEquals(listOf("a.jpg"), result.failed)
        assertEquals(listOf("b.jpg"), result.moved)
        assertEquals(setOf("a.jpg"), names(year))
        assertEquals(1, control.failedFiles)
    }
}
