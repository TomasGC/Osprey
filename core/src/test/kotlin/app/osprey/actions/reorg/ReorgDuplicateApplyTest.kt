package app.osprey.actions.reorg

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.RecordingJobControl
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.settings.InputMode
import java.io.IOException
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorgDuplicateApplyTest {

    private val fs = FakeFileSystemRepository()
    private val reader = FakeMediaMetadataReader()
    private val planner = ReorgPlanner(fs, reader)
    private val output = fs.addFolder(fs.root, "Library")
    private val camera = fs.addFolder(fs.root, "Camera")

    private fun file(parent: FileHandle, name: String, year: Int?, content: String = "same"): FileHandle {
        val handle = fs.addFile(parent, name, content.toByteArray())
        if (year != null) {
            reader.setCaptureDateTime(handle, MediaDate(LocalDateTime.of(year, 6, 15, 12, 0), MediaDateSource.EXIF))
        }
        return handle
    }

    private fun names(parent: FileHandle, vararg path: String): Set<String> {
        var current = parent
        for (segment in path) {
            current = fs.list(current).firstOrNull { it.isDirectory && it.name == segment }?.handle ?: return emptySet()
        }
        return fs.list(current).map { it.name }.toSet()
    }

    private fun choose(plan: ReorgPlan, keepInput: Boolean, keepLibrary: Boolean) =
        plan.duplicates.map { DuplicateChoice(it, keepInput, keepLibrary) }

    private fun moveCamera() = listOf(ReorgSource(camera, "Camera", InputMode.MOVE))

    private fun run(
        keepInput: Boolean,
        keepLibrary: Boolean,
        control: RecordingJobControl = RecordingJobControl(),
        using: ReorgPlanner = planner,
    ): ReorgResult {
        val plan = using.plan(output, moveCamera())
        return using.apply(plan, output, choose(plan, keepInput, keepLibrary), control)
    }

    @Test
    fun `an unticked input file goes to the Trash and the library file stays`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = false, keepLibrary = true)

        assertEquals(1, result.trashed)
        assertEquals(emptyList<ReorgFailure>(), result.failures)
        assertEquals(setOf("y.jpg"), names(output, "Trash", "2018", "Reorg"))
        assertEquals(emptySet<String>(), names(camera))
        assertEquals(setOf("x.jpg"), names(output, "2018"))
    }

    @Test
    fun `an unticked library file goes to the Trash and the input file stays`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = true, keepLibrary = false)

        assertEquals(1, result.trashed)
        assertEquals(setOf("x.jpg"), names(output, "Trash", "2018", "Reorg"))
        assertEquals(setOf("y.jpg"), names(camera))
        assertEquals(emptySet<String>(), names(output, "2018"))
    }

    @Test
    fun `two unticked files go to the Trash and a taken name gets a dup suffix`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "x.jpg", 2018)

        val result = run(keepInput = false, keepLibrary = false)

        assertEquals(2, result.trashed)
        assertEquals(setOf("x.jpg", "x__dup1.jpg"), names(output, "Trash", "2018", "Reorg"))
        assertEquals(emptySet<String>(), names(camera))
    }

    @Test
    fun `two ticked files change nothing`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = true, keepLibrary = true)

        assertEquals(0, result.trashed)
        assertEquals(setOf("y.jpg"), names(camera))
        assertEquals(setOf("x.jpg"), names(output, "2018"))
        assertEquals(emptySet<String>(), names(output, "Trash"))
    }

    @Test
    fun `a misfiled library file sent to the Trash is not refiled`() {
        file(fs.addFolder(output, "2019"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)
        val control = RecordingJobControl()

        val result = run(keepInput = true, keepLibrary = false, control = control)

        assertEquals(0, result.refiled)
        assertEquals(1, result.trashed)
        assertEquals(emptyList<ReorgFailure>(), result.failures)
        assertEquals(setOf("x.jpg"), names(output, "Trash", "2019", "Reorg"))
        assertEquals(emptySet<String>(), names(output, "2018"))
        assertEquals(
            listOf(
                FileRecord("2019/x.jpg", FileResult.Done, "moved to Trash/2019/Reorg/x.jpg (its refile was dropped)"),
            ),
            control.files,
        )
    }

    @Test
    fun `a misfiled library file that is kept is still refiled`() {
        file(fs.addFolder(output, "2019"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = false, keepLibrary = true)

        assertEquals(1, result.refiled)
        assertEquals(setOf("x.jpg"), names(output, "2018"))
    }

    @Test
    fun `a Trash copy that differs from the input is discarded and the input is kept`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)
        val lying = ReorgPlanner(SizeLyingFs(fs), reader)

        val result = run(keepInput = false, keepLibrary = true, using = lying)

        assertEquals(0, result.trashed)
        assertEquals("the copy differs from the source", result.failures.single().reason)
        assertEquals("Camera/y.jpg", result.failures.single().label)
        assertEquals(setOf("y.jpg"), names(camera))
        assertEquals(emptySet<String>(), names(output, "Trash", "2018", "Reorg"))
    }

    @Test
    fun `a failed delete of the input is reported and the next pair is still handled`() {
        val twin = fs.addFolder(output, "2018")
        file(twin, "x1.jpg", 2018, content = "one")
        file(twin, "x2.jpg", 2018, content = "two")
        val first = file(camera, "y1.jpg", 2018, content = "one")
        file(camera, "y2.jpg", 2018, content = "two")
        val faulty = FaultyFileSystemRepository(fs)
        faulty.failDelete = { if (it == first) IOException("locked") else null }

        val result = run(keepInput = false, keepLibrary = true, using = ReorgPlanner(faulty, reader))

        assertEquals(1, result.trashed)
        assertEquals(listOf(ReorgFailure("Camera/y1.jpg", "locked")), result.failures)
        assertEquals(setOf("y1.jpg"), names(camera))
        assertEquals(setOf("y1.jpg", "y2.jpg"), names(output, "Trash", "2018", "Reorg"))
    }

    @Test
    fun `an undated input file is filed under the library file's year`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", null)

        val result = run(keepInput = false, keepLibrary = true)

        assertEquals(1, result.trashed)
        assertEquals(setOf("y.jpg"), names(output, "Trash", "2018", "Reorg"))
    }

    @Test
    fun `a library file at the output root is filed under the input file's year`() {
        file(output, "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = true, keepLibrary = false)

        assertEquals(1, result.trashed)
        assertEquals(setOf("x.jpg"), names(output, "Trash", "2018", "Reorg"))
    }

    @Test
    fun `a file with no year on either side cannot be filed and is reported`() {
        file(output, "x.jpg", null)
        file(camera, "y.jpg", null)

        val result = run(keepInput = false, keepLibrary = true)

        assertEquals(0, result.trashed)
        assertEquals(listOf(ReorgFailure("Camera/y.jpg", "no year to file it under")), result.failures)
        assertEquals(setOf("y.jpg"), names(camera))
    }

    @Test
    fun `every trashed file is recorded with where it went`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)
        val control = RecordingJobControl()

        run(keepInput = false, keepLibrary = false, control = control)

        assertEquals(
            setOf(
                FileRecord("Camera/y.jpg", FileResult.Done, "moved to Trash/2018/Reorg/y.jpg"),
                FileRecord("2018/x.jpg", FileResult.Done, "moved to Trash/2018/Reorg/x.jpg"),
            ),
            control.files.toSet(),
        )
    }

    @Test
    fun `a stop after the first file leaves the second pair untouched`() {
        val twin = fs.addFolder(output, "2018")
        file(twin, "x1.jpg", 2018, content = "one")
        file(twin, "x2.jpg", 2018, content = "two")
        file(camera, "y1.jpg", 2018, content = "one")
        file(camera, "y2.jpg", 2018, content = "two")

        val result = run(keepInput = false, keepLibrary = true, control = RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, result.trashed)
        assertEquals(true, result.cancelled)
        assertEquals(setOf("y2.jpg"), names(camera))
    }

    @Test
    fun `a stop before the planned files are done trashes nothing`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)
        file(camera, "z.jpg", 2019, content = "other")

        val result = run(keepInput = false, keepLibrary = false, control = RecordingJobControl(stopAfterReports = 0))

        assertEquals(true, result.cancelled)
        assertEquals(0, result.trashed)
        assertEquals(setOf("y.jpg", "z.jpg"), names(camera))
        assertEquals(setOf("x.jpg"), names(output, "2018"))
    }

    @Test
    fun `a library file in a folder that is no year is filed under the input file's year`() {
        file(fs.addFolder(output, "Holiday"), "x.jpg", 2018)
        file(camera, "y.jpg", 2018)

        val result = run(keepInput = true, keepLibrary = false)

        assertEquals(1, result.trashed)
        assertEquals(setOf("x.jpg"), names(output, "Trash", "2018", "Reorg"))
    }

    /** A filesystem that lists every copy one byte larger than it is. */
    private class SizeLyingFs(private val inner: FileSystemRepository) : FileSystemRepository by inner {
        private val copies = mutableSetOf<FileHandle>()

        override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle =
            inner.copyTo(entry, destFolder, newName).also { copies += it }

        override fun list(folder: FileHandle): List<FileEntry> =
            inner.list(folder).map { if (it.handle in copies) it.copy(sizeBytes = it.sizeBytes + 1) else it }
    }
}
