package app.osprey.actions.reorg

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobProgress
import app.osprey.jobs.RecordingJobControl
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.settings.InputMode
import java.io.IOException
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorgPlannerTest {

    private val fs = FakeFileSystemRepository()
    private val reader = FakeMediaMetadataReader()
    private val planner = ReorgPlanner(fs, reader)
    private val output = fs.addFolder(fs.root, "Library")
    private val camera = fs.addFolder(fs.root, "Camera")
    private val whatsapp = fs.addFolder(fs.root, "WhatsApp")

    private fun exif(year: Int) = MediaDate(LocalDateTime.of(year, 6, 15, 12, 0), MediaDateSource.EXIF)

    private fun file(parent: FileHandle, name: String, year: Int?, content: String = name): FileHandle {
        val handle = fs.addFile(parent, name, content.toByteArray())
        if (year != null) reader.setCaptureDateTime(handle, exif(year))
        return handle
    }

    private fun copyFrom(folder: FileHandle, label: String = "Camera") =
        ReorgSource(folder, label, InputMode.COPY)

    private fun moveFrom(folder: FileHandle, label: String = "Camera") =
        ReorgSource(folder, label, InputMode.MOVE)

    private fun folder(parent: FileHandle, name: String): FileHandle =
        fs.list(parent).single { it.isDirectory && it.name == name }.handle

    private fun names(folder: FileHandle) = fs.list(folder).map { it.name }.toSet()

    private fun yearNames(year: Int) = names(folder(output, year.toString()))

    @Test
    fun `plan sends an input file to its capture year`() {
        file(camera, "a.jpg", 2018)

        val item = planner.plan(output, listOf(copyFrom(camera))).items.single()

        assertEquals(2018, item.year)
        assertEquals("a.jpg", item.destName)
        assertEquals(ReorgOperation.COPY, item.operation)
        assertEquals("Camera/a.jpg", item.label)
    }

    @Test
    fun `plan marks the files of a Move input as moves`() {
        file(camera, "a.jpg", 2018)

        val item = planner.plan(output, listOf(moveFrom(camera))).items.single()

        assertEquals(ReorgOperation.MOVE, item.operation)
    }

    @Test
    fun `plan gathers from several inputs`() {
        file(camera, "a.jpg", 2018)
        file(whatsapp, "b.jpg", 2019)

        val plan = planner.plan(output, listOf(copyFrom(camera), moveFrom(whatsapp, "WhatsApp")))

        assertEquals(setOf("Camera/a.jpg", "WhatsApp/b.jpg"), plan.items.map { it.label }.toSet())
    }

    @Test
    fun `plan scans input subfolders recursively`() {
        val sub = fs.addFolder(camera, "2020-trip")
        file(sub, "deep.jpg", 2020)

        val item = planner.plan(output, listOf(copyFrom(camera))).items.single()

        assertEquals("Camera/2020-trip/deep.jpg", item.label)
    }

    @Test
    fun `plan resolves the year from the file name when there is no metadata`() {
        file(camera, "IMG_20160828_193034.jpg", null)

        assertEquals(2016, planner.plan(output, listOf(copyFrom(camera))).items.single().year)
    }

    @Test
    fun `plan ignores files that are neither image nor video`() {
        file(camera, "notes.txt", 2018)

        val plan = planner.plan(output, listOf(copyFrom(camera)))

        assertEquals(emptyList<ReorgItem>(), plan.items)
        assertEquals(emptyList<String>(), plan.undated)
    }

    @Test
    fun `plan lists an undated file and leaves it out`() {
        file(camera, "mystery.jpg", null)

        val plan = planner.plan(output, listOf(copyFrom(camera)))

        assertEquals(emptyList<ReorgItem>(), plan.items)
        assertEquals(listOf("Camera/mystery.jpg"), plan.undated)
    }

    @Test
    fun `plan refiles a misfiled output file`() {
        file(fs.addFolder(output, "2019"), "a.jpg", 2016)

        val item = planner.plan(output, emptyList()).items.single()

        assertEquals(ReorgOperation.REFILE, item.operation)
        assertEquals(2016, item.year)
        assertEquals("2019/a.jpg", item.label)
    }

    @Test
    fun `plan refiles a loose file at the output root`() {
        file(output, "a.jpg", 2017)

        assertEquals(2017, planner.plan(output, emptyList()).items.single().year)
    }

    @Test
    fun `plan leaves an output file already in its year folder`() {
        file(fs.addFolder(output, "2019"), "a.jpg", 2019)

        assertEquals(emptyList<ReorgItem>(), planner.plan(output, emptyList()).items)
    }

    @Test
    fun `plan brings a file nested in a year folder up to the year folder`() {
        file(fs.addFolder(fs.addFolder(output, "2019"), "sub"), "a.jpg", 2019)

        val item = planner.plan(output, emptyList()).items.single()

        assertEquals(2019, item.year)
        assertEquals(ReorgOperation.REFILE, item.operation)
    }

    @Test
    fun `plan never reads the Trash or legacy TODELETE folders as sources`() {
        file(fs.addFolder(fs.addFolder(output, "Trash"), "2016"), "t.jpg", 2018)
        file(fs.addFolder(output, "TODELETEDUPS"), "d.jpg", 2018)
        file(fs.addFolder(camera, "Trash"), "c.jpg", 2018)
        file(fs.addFolder(camera, "TODELETE"), "e.jpg", 2018)

        assertEquals(emptyList<ReorgItem>(), planner.plan(output, listOf(copyFrom(camera))).items)
    }

    @Test
    fun `plan skips an input file whose content is already in the output under another name`() {
        file(fs.addFolder(output, "2018"), "2018-06-15_12-00-00.jpg", 2018, content = "same")
        file(camera, "IMG_1.jpg", 2018, content = "same")

        val plan = planner.plan(output, listOf(copyFrom(camera)))

        assertEquals(emptyList<ReorgItem>(), plan.items)
        assertEquals(listOf("Camera/IMG_1.jpg"), plan.alreadyInOutput)
    }

    @Test
    fun `plan keeps a file that only has the same size as an output file`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018, content = "aaaa")
        file(camera, "y.jpg", 2018, content = "bbbb")

        assertEquals(1, planner.plan(output, listOf(copyFrom(camera))).items.size)
    }

    @Test
    fun `plan takes one of two inputs holding the same content`() {
        file(camera, "a.jpg", 2018, content = "same")
        file(whatsapp, "b.jpg", 2018, content = "same")

        val plan = planner.plan(output, listOf(copyFrom(camera), copyFrom(whatsapp, "WhatsApp")))

        assertEquals(listOf("Camera/a.jpg"), plan.items.map { it.label })
        assertEquals(listOf("WhatsApp/b.jpg"), plan.alreadyInOutput)
    }

    @Test
    fun `plan suffixes a name taken in the destination year folder`() {
        file(fs.addFolder(output, "2018"), "a.jpg", 2018, content = "old")
        file(camera, "a.jpg", 2018, content = "new")

        val item = planner.plan(output, listOf(copyFrom(camera))).items.single()

        assertEquals("a__year1.jpg", item.destName)
        assertTrue(item.note.isNotEmpty())
    }

    @Test
    fun `plan gives two same-named inputs distinct names`() {
        file(camera, "a.jpg", 2018, content = "one")
        file(whatsapp, "a.jpg", 2018, content = "two")

        val plan = planner.plan(output, listOf(copyFrom(camera), copyFrom(whatsapp, "WhatsApp")))

        assertEquals(setOf("a.jpg", "a__year1.jpg"), plan.items.map { it.destName }.toSet())
    }

    @Test
    fun `plan reports an unreachable input and still plans the others`() {
        file(whatsapp, "b.jpg", 2019)
        val sources = listOf(ReorgSource(null, "SD card", InputMode.MOVE), copyFrom(whatsapp, "WhatsApp"))

        val plan = planner.plan(output, sources)

        assertEquals(listOf("SD card"), plan.unreachable)
        assertEquals(1, plan.items.size)
    }

    @Test
    fun `apply copies and keeps the source`() {
        val source = file(camera, "a.jpg", 2018)

        val result = planner.apply(planner.plan(output, listOf(copyFrom(camera))), output)

        assertEquals(1, result.copied)
        assertEquals(setOf("a.jpg"), yearNames(2018))
        assertEquals(setOf("a.jpg"), names(camera))
        assertEquals(fs.contentHash(source), fs.contentHash(fs.list(folder(output, "2018")).single().handle))
    }

    @Test
    fun `apply moves and removes the source once verified`() {
        file(camera, "a.jpg", 2018)

        val result = planner.apply(planner.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(1, result.moved)
        assertEquals(setOf("a.jpg"), yearNames(2018))
        assertEquals(emptySet<String>(), names(camera))
    }

    @Test
    fun `apply refiles inside the output`() {
        file(fs.addFolder(output, "2019"), "a.jpg", 2016)

        val result = planner.apply(planner.plan(output, emptyList()), output)

        assertEquals(1, result.refiled)
        assertEquals(setOf("a.jpg"), yearNames(2016))
        assertEquals(emptySet<String>(), yearNames(2019))
    }

    @Test
    fun `a Copy rerun plans nothing, even after the output copy was renamed`() {
        file(camera, "IMG_1.jpg", 2018)
        planner.apply(planner.plan(output, listOf(copyFrom(camera))), output)
        val copy = fs.list(folder(output, "2018")).single().handle
        fs.rename(copy, "2018-06-15_12-00-00.jpg")
        reader.setCaptureDateTime(copy, exif(2018))

        val second = planner.plan(output, listOf(copyFrom(camera)))

        assertEquals(emptyList<ReorgItem>(), second.items)
    }

    @Test
    fun `a rerun after Move plans nothing`() {
        file(camera, "a.jpg", 2018)
        planner.apply(planner.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(emptyList<ReorgItem>(), planner.plan(output, listOf(moveFrom(camera))).items)
    }

    @Test
    fun `apply keeps the source and discards the copy when verification fails`() {
        file(camera, "a.jpg", 2018)
        val corruptPlanner = ReorgPlanner(CorruptingFs(fs), reader)

        val result = corruptPlanner.apply(corruptPlanner.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(0, result.moved)
        assertEquals(1, result.failures.size)
        assertEquals("Camera/a.jpg", result.failures.single().label)
        assertEquals(setOf("a.jpg"), names(camera))
        assertEquals(emptySet<String>(), yearNames(2018))
    }

    @Test
    fun `apply keeps both files when the source cannot be deleted`() {
        file(camera, "a.jpg", 2018)
        val stuckPlanner = ReorgPlanner(StuckDeleteFs(fs), reader)

        val result = stuckPlanner.apply(stuckPlanner.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(1, result.failures.size)
        assertEquals(setOf("a.jpg"), names(camera))
        assertEquals(setOf("a.jpg"), yearNames(2018))
    }

    @Test
    fun `apply carries on after a file fails and reports it`() {
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2018)
        val plan = planner.plan(output, listOf(copyFrom(camera)))
        fs.addFile(fs.createFolder(output, "2018"), "a.jpg", byteArrayOf(9)) // appears after planning

        val result = planner.apply(plan, output)

        assertEquals(1, result.copied)
        assertEquals(listOf("Camera/a.jpg"), result.failures.map { it.label })
        assertEquals(setOf("a.jpg", "b.jpg"), names(camera))
    }

    @Test
    fun `apply stops between files when asked`() {
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2018)

        val result = planner.apply(
            planner.plan(output, listOf(moveFrom(camera))),
            output,
            control = RecordingJobControl(stopAfterReports = 1),
        )

        assertTrue(result.cancelled)
        assertEquals(1, result.moved)
        assertEquals(1, names(camera).size)
    }

    @Test
    fun `apply reports progress for every file`() {
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2019)
        val control = RecordingJobControl()

        planner.apply(planner.plan(output, listOf(copyFrom(camera))), output, control = control)

        assertEquals(listOf(0, 1), control.reports.map { it.done })
        assertEquals(setOf(2), control.reports.map { it.total }.toSet())
        assertEquals(setOf("Camera/a.jpg", "Camera/b.jpg"), control.reports.map { it.current }.toSet())
    }

    @Test
    fun `describe logs one line per item, undated and unreachable`() {
        file(camera, "a.jpg", 2018)
        file(camera, "mystery.jpg", null)
        val plan = planner.plan(output, listOf(copyFrom(camera), ReorgSource(null, "SD card", InputMode.COPY)))
        val lines = mutableListOf<String>()

        val count = planner.describe(plan, lines::add)

        assertEquals(1, count)
        assertTrue(lines.any { it.contains("COPY Camera/a.jpg -> 2018/a.jpg") })
        assertTrue(lines.any { it.contains("UNDATED Camera/mystery.jpg") })
        assertTrue(lines.any { it.contains("UNREACHABLE SD card") })
    }

    private fun failureOf(error: Exception): ReorgFailure {
        file(camera, "a.jpg", 2018)
        val faulty = FaultyFileSystemRepository(fs).also { it.failCopy = { error } }
        val failing = ReorgPlanner(faulty, reader)

        val result = failing.apply(failing.plan(output, listOf(copyFrom(camera))), output)

        assertEquals(0, result.copied)
        assertEquals(setOf("a.jpg"), names(camera))
        return result.failures.single()
    }

    @Test
    fun `apply reports an I-O error and leaves the source`() {
        assertEquals(ReorgFailure("Camera/a.jpg", "disk full"), failureOf(IOException("disk full")))
    }

    @Test
    fun `apply reports a name that is already taken`() {
        assertEquals(ReorgFailure("Camera/a.jpg", "taken"), failureOf(FileAlreadyExistsException("taken")))
    }

    @Test
    fun `apply reports a folder that is not empty`() {
        assertEquals(ReorgFailure("Camera/a.jpg", "not empty"), failureOf(DirectoryNotEmptyException("not empty")))
    }

    @Test
    fun `apply reports a state error`() {
        assertEquals(ReorgFailure("Camera/a.jpg", "gone"), failureOf(IllegalStateException("gone")))
    }

    @Test
    fun `apply reports a refused permission`() {
        assertEquals(ReorgFailure("Camera/a.jpg", "denied"), failureOf(SecurityException("denied")))
    }

    @Test
    fun `apply discards a copy that cannot be found in the destination`() {
        file(camera, "a.jpg", 2018)
        val misplacing = ReorgPlanner(MisnamingCopyFs(fs), reader)

        val result = misplacing.apply(misplacing.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(0, result.moved)
        assertEquals("the copy differs from the source", result.failures.single().reason)
        assertEquals(setOf("a.jpg"), names(camera))
        assertEquals(emptySet<String>(), yearNames(2018))
    }

    @Test
    fun `apply discards a copy whose size differs from the source`() {
        file(camera, "a.jpg", 2018)
        val lying = ReorgPlanner(SizeLyingFs(fs), reader)

        val result = lying.apply(lying.plan(output, listOf(moveFrom(camera))), output)

        assertEquals(0, result.moved)
        assertEquals("the copy differs from the source", result.failures.single().reason)
        assertEquals(setOf("a.jpg"), names(camera))
        assertEquals(emptySet<String>(), yearNames(2018))
    }

    @Test
    fun `describe marks a renamed item with its note`() {
        file(fs.addFolder(output, "2018"), "a.jpg", 2018, content = "old")
        file(camera, "a.jpg", 2018, content = "new")
        val lines = mutableListOf<String>()

        planner.describe(planner.plan(output, listOf(copyFrom(camera))), lines::add)

        assertEquals(listOf("  COPY Camera/a.jpg -> 2018/a__year1.jpg  (renamed, name collision)"), lines)
    }

    @Test
    fun `describe logs a skipped file as already in the output`() {
        file(fs.addFolder(output, "2018"), "x.jpg", 2018, content = "same")
        file(camera, "y.jpg", 2018, content = "same")
        val lines = mutableListOf<String>()

        planner.describe(planner.plan(output, listOf(copyFrom(camera))), lines::add)

        assertEquals(listOf("  SKIP Camera/y.jpg  (content already in the output)"), lines)
    }

    @Test
    fun `an undated output file is listed and left where it is`() {
        file(fs.addFolder(output, "2019"), "mystery.jpg", null)

        val plan = planner.plan(output, emptyList())

        assertEquals(listOf("2019/mystery.jpg"), plan.undated)
        assertEquals(emptyList<ReorgItem>(), plan.items)
    }

    @Test
    fun `plan reports every file it looks at, the output first and then each input`() {
        file(fs.addFolder(output, "2019"), "o.jpg", 2016)
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2018)
        val control = RecordingJobControl()

        planner.plan(output, listOf(copyFrom(camera)), control)

        assertEquals(
            listOf(
                JobProgress(0, 3, "2019/o.jpg"),
                JobProgress(1, 3, "Camera/a.jpg"),
                JobProgress(2, 3, "Camera/b.jpg"),
            ),
            control.reports,
        )
    }

    @Test
    fun `plan stops looking when asked and keeps what it had planned`() {
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2018)

        val plan = planner.plan(output, listOf(copyFrom(camera)), RecordingJobControl(stopAfterReports = 1))

        assertEquals(listOf("Camera/a.jpg"), plan.items.map { it.label })
    }

    @Test
    fun `a run stopped midway and planned again finds exactly the files left`() {
        file(camera, "a.jpg", 2018)
        file(camera, "b.jpg", 2018)
        val first = planner.plan(output, listOf(moveFrom(camera)))
        planner.apply(first, output, control = RecordingJobControl(stopAfterReports = 1))

        val second = planner.plan(output, listOf(moveFrom(camera)))

        assertEquals(1, names(camera).size)
        assertEquals(listOf("Camera/" + names(camera).single()), second.items.map { it.label })
    }

    /** A filesystem whose copies hash differently from their source. */
    private class CorruptingFs(private val inner: FileSystemRepository) : FileSystemRepository by inner {
        private val copies = mutableSetOf<FileHandle>()

        override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle =
            inner.copyTo(entry, destFolder, newName).also { copies += it }

        override fun contentHash(entry: FileHandle): String =
            if (entry in copies) "corrupt" else inner.contentHash(entry)
    }

    /** A filesystem that refuses every delete, so a verified source cannot be removed. */
    private class StuckDeleteFs(private val inner: FileSystemRepository) : FileSystemRepository by inner {
        override fun delete(entry: FileHandle) = throw FileAlreadyExistsException("locked")
    }

    /** A filesystem whose copies land under another name, so the destination does not list them by their name. */
    private class MisnamingCopyFs(private val inner: FileSystemRepository) : FileSystemRepository by inner {
        override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle =
            inner.copyTo(entry, destFolder, "${newName ?: "copy"}.part")
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
