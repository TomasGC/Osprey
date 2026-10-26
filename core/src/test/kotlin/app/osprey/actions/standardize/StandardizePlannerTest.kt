package app.osprey.actions.standardize

import app.osprey.jobs.RecordingJobControl
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardizePlannerTest {

    @Test
    fun `plan renames an image via filename fallback`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "download_20160828_193034.jpeg", "not a real jpeg".toByteArray())
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<Any>(), plan.unfixable)
        assertEquals(1, plan.renames.size)
        val rename = plan.renames.single()
        assertEquals("download_20160828_193034.jpeg", rename.sourceName)
        assertEquals("IMG_2016-08-28_19-30-34.jpeg", rename.targetName)
        assertEquals(LocalDateTime.of(2016, 8, 28, 19, 30, 34), rename.dateTime)
        assertEquals(MediaDateSource.FILENAME, rename.source)
    }

    @Test
    fun `plan renames from the filename when the reader date is before 1970`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20170425_152602.mp4")
        reader.setCaptureDateTime(video, MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0, 0), MediaDateSource.VIDEO_METADATA))

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        assertEquals("VID_2017-04-25_15-26-02.mp4", rename.targetName)
        assertEquals(MediaDateSource.FILENAME, rename.source)
    }

    @Test
    fun `plan uses the VID prefix for a video`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "VID_20170425_152602.mp4")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.renames.size)
        assertEquals("VID_2017-04-25_15-26-02.mp4", plan.renames.single().targetName)
    }

    @Test
    fun `plan skips an unsupported extension`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_20170425_152602.txt")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<StandardizeRename>(), plan.renames)
        assertEquals(emptyList<Any>(), plan.unfixable)
    }

    @Test
    fun `plan leaves an already-standardized name unchanged`() {
        // The standardized dashed format isn't one of the filename patterns, so a real
        // EXIF-backed resolution isolates the "target == source" skip from date-resolution
        // concerns -- same trap the POC's own test suite hit once (see #7's resolution note).
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "IMG_2017-04-25_15-26-02.jpg", "x".toByteArray())
        val reader = FakeMediaMetadataReader()
        reader.setCaptureDateTime(entry, MediaDate(LocalDateTime.of(2017, 4, 25, 15, 26, 2), MediaDateSource.EXIF))
        val planner = StandardizePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<StandardizeRename>(), plan.renames)
        assertEquals(emptyList<Any>(), plan.unfixable)
    }

    @Test
    fun `plan marks a file unfixable when no date resolves`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "random.jpg", "not a real jpeg".toByteArray())
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<StandardizeRename>(), plan.renames)
        assertEquals(listOf("random.jpg"), plan.unfixable.map { it.name })
    }

    @Test
    fun `plan suffixes a destination collision starting at __dup2`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_2017-04-25_15-26-02.jpg", "existing standardized file".toByteArray())
        fs.addFile(fs.root, "IMG_20170425_152602.jpg", "not a real jpeg".toByteArray())
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.renames.size)
        assertEquals("IMG_2017-04-25_15-26-02__dup2.jpg", plan.renames.single().targetName)
    }

    @Test
    fun `plan skips TODELETE and TODELETEDUPS`() {
        val fs = FakeFileSystemRepository()
        val todelete = fs.addFolder(fs.root, "TODELETE")
        fs.addFile(todelete, "IMG_20170425_152602.jpg")
        val todeleteDups = fs.addFolder(fs.root, "TODELETEDUPS")
        fs.addFile(todeleteDups, "IMG_20170425_152602.jpg")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<StandardizeRename>(), plan.renames)
        assertEquals(emptyList<Any>(), plan.unfixable)
    }

    @Test
    fun `apply renames on the repository`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "download_20160828_193034.jpeg", "x".toByteArray())
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val plan = planner.plan(fs.root)

        planner.apply(plan)

        assertEquals(listOf("IMG_2016-08-28_19-30-34.jpeg"), fs.list(fs.root).map { it.name })
    }

    @Test
    fun `describe counts renames and logs them`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "download_20160828_193034.jpeg", "x".toByteArray())
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val plan = planner.plan(fs.root)
        val logs = mutableListOf<String>()

        val count = planner.describe(plan, logs::add)

        assertEquals(1, count)
        assertTrue(logs.any { it.contains("RENAME") })
    }

    @Test
    fun `plan gives two files that resolve to one name distinct names`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_20170425_152602.jpg")
        fs.addFile(fs.root, "download_20170425_152602.jpg")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val targets = planner.plan(fs.root).renames.map { it.targetName }

        assertEquals(setOf("IMG_2017-04-25_15-26-02.jpg", "IMG_2017-04-25_15-26-02__dup2.jpg"), targets.toSet())
        assertEquals(2, targets.size)
    }

    @Test
    fun `plan numbers the next duplicate when the first suffix is taken too`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_2017-04-25_15-26-02.jpg", "a".toByteArray())
        fs.addFile(fs.root, "IMG_2017-04-25_15-26-02__dup2.jpg", "b".toByteArray())
        fs.addFile(fs.root, "download_20170425_152602.jpg", "c".toByteArray())

        val rename = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root).renames.single()

        assertEquals("IMG_2017-04-25_15-26-02__dup3.jpg", rename.targetName)
    }

    @Test
    fun `describe logs an unfixable file before the renames`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "random.jpg")
        fs.addFile(fs.root, "download_20160828_193034.jpeg")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val logs = mutableListOf<String>()

        val count = planner.describe(planner.plan(fs.root), logs::add)

        assertEquals(1, count)
        assertEquals("  UNFIXABLE random.jpg", logs.first())
        assertTrue(logs.last().startsWith("  RENAME download_20160828_193034.jpeg -> IMG_2016-08-28_19-30-34.jpeg"))
    }

    private fun twoRenames(fs: FakeFileSystemRepository) {
        fs.addFile(fs.root, "download_20160828_193034.jpeg", "a".toByteArray())
        fs.addFile(fs.root, "download_20170101_000000.jpeg", "b".toByteArray())
    }

    @Test
    fun `plan reports each file`() {
        val fs = FakeFileSystemRepository()
        twoRenames(fs)
        val control = RecordingJobControl()

        StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root, control)

        assertEquals(
            listOf("download_20160828_193034.jpeg", "download_20170101_000000.jpeg"),
            control.reports.map { it.current },
        )
        assertEquals(setOf(2), control.reports.map { it.total }.toSet())
    }

    @Test
    fun `plan stops when asked and keeps what it had`() {
        val fs = FakeFileSystemRepository()
        twoRenames(fs)

        val plan = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root, RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, plan.renames.size)
    }

    @Test
    fun `apply reports each rename and returns how many it made`() {
        val fs = FakeFileSystemRepository()
        twoRenames(fs)
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val control = RecordingJobControl()

        val renamed = planner.apply(planner.plan(fs.root), control)

        assertEquals(2, renamed)
        assertEquals(2, control.reports.size)
    }

    @Test
    fun `a stopped apply leaves the other file and a new scan finds it`() {
        val fs = FakeFileSystemRepository()
        twoRenames(fs)
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        val renamed = planner.apply(planner.plan(fs.root), RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, renamed)
        assertEquals(1, planner.plan(fs.root).renames.size)
    }
}
