package app.osprey.actions.dates

import java.time.ZoneOffset
import java.time.ZoneId
import app.osprey.jobs.RecordingJobControl
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileEntry
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesPlannerTest {

    @Test
    fun `plan marks a file unfixable when nothing resolves`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "random.jpg", "not a real jpeg".toByteArray())
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<DatesFix>(), plan.fixes)
        assertEquals(listOf("random.jpg"), plan.unfixable.map { it.name })
    }

    @Test
    fun `plan falls back to the filename when metadata resolves nothing`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_20170425_152602.jpg", "not a real jpeg".toByteArray())
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<FileEntry>(), plan.unfixable)
        assertEquals(1, plan.fixes.size)
        val fix = plan.fixes.single()
        assertEquals("IMG_20170425_152602.jpg", fix.name)
        assertEquals(LocalDateTime.of(2017, 4, 25, 15, 26, 2), fix.dateTime)
        assertEquals(MediaDateSource.FILENAME, fix.source)
    }

    @Test
    fun `plan skips the TODELETE folder entirely`() {
        val fs = FakeFileSystemRepository()
        val todelete = fs.addFolder(fs.root, "TODELETE")
        fs.addFile(todelete, "IMG_20170425_152602.jpg")
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<DatesFix>(), plan.fixes)
        assertEquals(emptyList<FileEntry>(), plan.unfixable)
    }

    @Test
    fun `plan ignores directories`() {
        val fs = FakeFileSystemRepository()
        fs.addFolder(fs.root, "subdir")
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<DatesFix>(), plan.fixes)
        assertEquals(emptyList<FileEntry>(), plan.unfixable)
    }

    @Test
    fun `plan ignores a reader date before 1970 and falls back to the filename`() {
        // An MP4 with no creation time reports its 1904 epoch (Android's MediaMetadataRetriever).
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20170425_152602.mp4")
        reader.setCaptureDateTime(video, MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0, 0), MediaDateSource.VIDEO_METADATA))

        val fix = DatesPlanner(fs, reader).plan(fs.root).fixes.single()

        assertEquals(LocalDateTime.of(2017, 4, 25, 15, 26, 2), fix.dateTime)
        assertEquals(MediaDateSource.FILENAME, fix.source)
    }

    @Test
    fun `plan ignores a year-zero EXIF date and falls back to the filename`() {
        // EXIF "0000:00:00 00:00:00" (an unset camera clock) parses to year 0 on desktop.
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val photo = fs.addFile(fs.root, "IMG_20230101_000000.jpg")
        reader.setCaptureDateTime(photo, MediaDate(LocalDateTime.of(-1, 11, 27, 23, 9, 2), MediaDateSource.EXIF))

        val fix = DatesPlanner(fs, reader).plan(fs.root).fixes.single()

        assertEquals(LocalDateTime.of(2023, 1, 1, 0, 0, 0), fix.dateTime)
        assertEquals(MediaDateSource.FILENAME, fix.source)
    }

    @Test
    fun `plan marks a file unfixable when its reader date is before 1970 and its name has no date`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val photo = fs.addFile(fs.root, "DSC_0210.jpg")
        reader.setCaptureDateTime(photo, MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0, 0), MediaDateSource.EXIF))

        val plan = DatesPlanner(fs, reader).plan(fs.root)

        assertEquals(emptyList<DatesFix>(), plan.fixes)
        assertEquals(listOf("DSC_0210.jpg"), plan.unfixable.map { it.name })
    }

    @Test
    fun `plan keeps a reader date of exactly 1970-01-01`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val photo = fs.addFile(fs.root, "DSC_0210.jpg")
        reader.setCaptureDateTime(photo, MediaDate(LocalDateTime.of(1970, 1, 1, 0, 0, 0), MediaDateSource.EXIF))

        val fix = DatesPlanner(fs, reader).plan(fs.root).fixes.single()

        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0, 0), fix.dateTime)
        assertEquals(MediaDateSource.EXIF, fix.source)
    }

    @Test
    fun `describe separates metadata from filename sources and logs the unfixable`() {
        val fs = FakeFileSystemRepository()
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())
        fs.addFile(fs.root, "d.jpg")
        val plan = DatesPlan(
            fixes = listOf(
                DatesFix(fs.addFile(fs.root, "a.jpg"), "a.jpg", LocalDateTime.of(2020, 1, 1, 0, 0, 0), MediaDateSource.EXIF),
                DatesFix(fs.addFile(fs.root, "b.jpg"), "b.jpg", LocalDateTime.of(2020, 1, 2, 0, 0, 0), MediaDateSource.VIDEO_METADATA),
                DatesFix(fs.addFile(fs.root, "c.jpg"), "c.jpg", LocalDateTime.of(2020, 1, 3, 0, 0, 0), MediaDateSource.FILENAME),
            ),
            unfixable = fs.list(fs.root).filter { it.name == "d.jpg" },
        )
        val logs = mutableListOf<String>()

        val (fixedFromMetadata, fixedFromFilename) = planner.describe(plan, logs::add)

        assertEquals(2, fixedFromMetadata)
        assertEquals(1, fixedFromFilename)
        assertTrue(logs.any { it.contains("UNFIXABLE") })
    }

    private fun twoFixes(fs: FakeFileSystemRepository) {
        fs.addFile(fs.root, "IMG_20170425_152602.jpg")
        fs.addFile(fs.root, "IMG_20180101_000000.jpg")
    }

    @Test
    fun `plan reports each file`() {
        val fs = FakeFileSystemRepository()
        twoFixes(fs)
        fs.addFolder(fs.root, "sub")
        val control = RecordingJobControl()

        DatesPlanner(fs, FakeMediaMetadataReader()).plan(fs.root, control)

        assertEquals(listOf("IMG_20170425_152602.jpg", "IMG_20180101_000000.jpg"), control.reports.map { it.current })
        assertEquals(setOf(2), control.reports.map { it.total }.toSet())
    }

    @Test
    fun `plan stops when asked and keeps what it had`() {
        val fs = FakeFileSystemRepository()
        twoFixes(fs)

        val plan = DatesPlanner(fs, FakeMediaMetadataReader()).plan(fs.root, RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, plan.fixes.size)
    }

    @Test
    fun `apply sets the created and modified time of every file from its resolved date`() {
        val fs = FakeFileSystemRepository()
        twoFixes(fs)
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())
        val plan = planner.plan(fs.root)
        val control = RecordingJobControl()

        val fixed = planner.apply(plan, ZoneOffset.UTC, control)

        assertEquals(2, fixed)
        val expected = LocalDateTime.of(2017, 4, 25, 15, 26, 2).toInstant(ZoneOffset.UTC)
        val first = fs.readTimestamps(plan.fixes.first { it.name == "IMG_20170425_152602.jpg" }.entry)
        assertEquals(expected, first.created)
        assertEquals(expected, first.modified)
        assertEquals(listOf("IMG_20170425_152602.jpg", "IMG_20180101_000000.jpg"), control.reports.map { it.current })
    }

    @Test
    fun `a stopped apply leaves the other files as they were`() {
        val fs = FakeFileSystemRepository()
        twoFixes(fs)
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())
        val plan = planner.plan(fs.root)

        val fixed = planner.apply(plan, ZoneOffset.UTC, RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, fixed)
        assertEquals(listOf(null), plan.fixes.map { fs.readTimestamps(it.entry).modified }.filter { it == null })
    }

    @Test
    fun `apply uses the system zone when none is given`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_20170425_152602.jpg")
        val planner = DatesPlanner(fs, FakeMediaMetadataReader())
        val plan = planner.plan(fs.root)

        planner.apply(plan)

        val expected = LocalDateTime.of(2017, 4, 25, 15, 26, 2).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expected, fs.readTimestamps(plan.fixes.single().entry).modified)
    }
}
