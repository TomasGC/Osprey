package app.osprey.actions.visual

import app.osprey.jobs.RecordingJobControl
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FirstMoveFails
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDupeMoveTest {

    private val fs = FakeFileSystemRepository()
    private val year = fs.addFolder(fs.root, "2019")
    private val trash = TrashLocation(fs.root, "2019")

    private fun write(name: String, sizeBytes: Int = 1): VisualDupeMember {
        val handle = fs.addFile(year, name, ByteArray(sizeBytes))
        return VisualDupeMember(handle, name, sizeBytes.toLong())
    }

    private fun exists(parent: FileHandle, name: String): Boolean = fs.list(parent).any { it.name == name }

    private fun trashFolder(): FileHandle = TrashFolders(fs).ensure(trash, TrashAction.VISUAL_DUPES)

    @Test
    fun `moves every non-kept member into Trash, year, VisualDupes`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")

        val result = moveUncheckedToTrash(fs, trash, year, listOf(keep, dup1, dup2), setOf("keep.jpg"))

        assertEquals(setOf("dup1.jpg", "dup2.jpg"), result.moved.toSet())
        assertTrue(result.skipped.isEmpty())
        assertTrue(exists(year, "keep.jpg"))
        assertTrue(exists(trashFolder(), "dup1.jpg"))
        assertTrue(exists(trashFolder(), "dup2.jpg"))
        assertTrue(fs.list(year).none { it.isDirectory })
    }

    @Test
    fun `keeping every member moves nothing and never creates Trash`() {
        val a = write("a.jpg")
        val b = write("b.jpg")

        val result = moveUncheckedToTrash(fs, trash, year, listOf(a, b), setOf("a.jpg", "b.jpg"))

        assertTrue(result.moved.isEmpty())
        assertTrue(result.skipped.isEmpty())
        assertTrue(fs.list(fs.root).none { it.name == "Trash" })
    }

    @Test
    fun `a name collision in the trash folder gets a __dup suffix`() {
        val keep = write("keep.jpg")
        val dup = write("photo.jpg")
        val folder = trashFolder()
        fs.addFile(folder, "photo.jpg", "already here".toByteArray())

        val result = moveUncheckedToTrash(fs, trash, year, listOf(keep, dup), setOf("keep.jpg"))

        assertEquals(listOf("photo.jpg"), result.moved)
        assertTrue(exists(folder, "photo__dup1.jpg"))
        assertEquals("already here", String(fs.readBytes(fs.list(folder).single { it.name == "photo.jpg" }.handle)))
    }

    @Test
    fun `a member whose file is already gone is skipped, not failed`() {
        val keep = write("keep.jpg")
        val vanished = VisualDupeMember(fs.addFile(year, "vanished.jpg"), "vanished.jpg", 0)
        fs.rename(vanished.handle, "renamed_away.jpg")

        val result = moveUncheckedToTrash(fs, trash, year, listOf(keep, vanished), setOf("keep.jpg"))

        assertTrue(result.moved.isEmpty())
        assertEquals(listOf("vanished.jpg"), result.skipped)
    }

    @Test
    fun `reports each member it moves`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")
        val control = RecordingJobControl()

        moveUncheckedToTrash(fs, trash, year, listOf(keep, dup1, dup2), setOf("keep.jpg"), control)

        assertEquals(listOf("dup1.jpg", "dup2.jpg"), control.reports.map { it.current })
        assertEquals(setOf(2), control.reports.map { it.total }.toSet())
    }

    @Test
    fun `stops between two members when asked and leaves the rest`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")

        val result = moveUncheckedToTrash(
            fs, trash, year, listOf(keep, dup1, dup2), setOf("keep.jpg"), RecordingJobControl(stopAfterReports = 1),
        )

        assertEquals(listOf("dup1.jpg"), result.moved)
        assertTrue(exists(year, "dup2.jpg"))
    }

    @Test
    fun `records each moved member with where it went`() {
        val keep = write("keep.jpg")
        val dup = write("dup.jpg")
        val control = RecordingJobControl()

        moveUncheckedToTrash(fs, trash, year, listOf(keep, dup), setOf("keep.jpg"), control)

        assertEquals(
            listOf(FileRecord("dup.jpg", FileResult.Done, "moved to Trash/2019/VisualDupes/dup.jpg")),
            control.files,
        )
    }

    @Test
    fun `records a member that is no longer in the folder as skipped`() {
        val keep = write("keep.jpg")
        val dup = write("dup.jpg")
        fs.delete(dup.handle)
        val control = RecordingJobControl()

        moveUncheckedToTrash(fs, trash, year, listOf(keep, dup), setOf("keep.jpg"), control)

        assertEquals(
            listOf(FileRecord("dup.jpg", FileResult.Skipped, "no longer in the folder")),
            control.files,
        )
    }

    @Test
    fun `a member that could not be moved is recorded as failed and the others still move`() {
        val keep = write("keep.jpg")
        val dup1 = write("dup1.jpg")
        val dup2 = write("dup2.jpg")
        val control = RecordingJobControl()

        val result = moveUncheckedToTrash(
            FirstMoveFails(fs), trash, year, listOf(keep, dup1, dup2), setOf("keep.jpg"), control,
        )

        assertEquals(listOf("dup2.jpg"), result.moved)
        assertEquals(listOf("dup1.jpg"), result.failed)
        assertEquals(listOf(FileResult.Failed, FileResult.Done), control.files.map { it.result })
        assertEquals("locked", control.files.first().message)
    }

    @Test
    fun `members whose trash names would collide with each other still all move`() {
        val keep = write("keep.jpg")
        val first = write("a.jpg")
        val second = write("a__dup1.jpg")
        fs.addFile(trashFolder(), "a.jpg", ByteArray(1)) // the Trash already holds a.jpg

        val result = moveUncheckedToTrash(fs, trash, year, listOf(keep, first, second), setOf("keep.jpg"))

        assertEquals(2, result.moved.size)
        assertEquals(3, fs.list(trashFolder()).size)
    }
}
