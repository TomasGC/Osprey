package app.osprey.actions.standardize

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.filesystem.FirstRenameFails
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import app.osprey.jobs.RecordingJobControl
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val DAY: Instant = Instant.parse("2019-05-04T12:00:00Z")

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
        reader.setCaptureDateTime(
            video,
            MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0, 0), MediaDateSource.VIDEO_METADATA),
        )

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        assertEquals("VID_2017-04-25_15-26-02.mp4", rename.targetName)
        assertEquals(MediaDateSource.FILENAME, rename.source)
    }

    @Test
    fun `plan names a video shot in another timezone after the time in its name`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20200710_134500.mp4")
        val container = utcToLocal(LocalDateTime.of(2020, 7, 10, 8, 0, 0))
        reader.setCaptureDateTime(video, MediaDate(container, MediaDateSource.VIDEO_METADATA))

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        assertEquals("VID_2020-07-10_13-45-00.mp4", rename.targetName)
        assertEquals(LocalDateTime.of(2020, 7, 10, 13, 45, 0), rename.dateTime)
        assertEquals(MediaDateSource.FILENAME, rename.source)
    }

    @Test
    fun `plan keeps the container time for a video whose name is no offset of it`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20200710_135500.mp4")
        val container = utcToLocal(LocalDateTime.of(2020, 7, 10, 8, 0, 0))
        reader.setCaptureDateTime(video, MediaDate(container, MediaDateSource.VIDEO_METADATA))

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        val containerName = container.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        assertEquals("VID_$containerName.mp4", rename.targetName)
        assertEquals(MediaDateSource.VIDEO_METADATA, rename.source)
    }

    @Test
    fun `plan leaves a video alone once it is renamed after another timezone`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20200710_134500.mp4")
        val container = utcToLocal(LocalDateTime.of(2020, 7, 10, 8, 0, 0))
        reader.setCaptureDateTime(video, MediaDate(container, MediaDateSource.VIDEO_METADATA))
        val planner = StandardizePlanner(fs, reader)

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(listOf("VID_2020-07-10_13-45-00.mp4"), fs.list(fs.root).map { it.name })
        assertEquals(emptyList<Any>(), second.renames)
        assertEquals(emptyList<Any>(), second.unfixable)
    }

    @Test
    fun `plan leaves a video alone once it is renamed after its container`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val video = fs.addFile(fs.root, "VID_20200710_135500.mp4")
        val container = utcToLocal(LocalDateTime.of(2020, 7, 10, 8, 0, 0))
        reader.setCaptureDateTime(video, MediaDate(container, MediaDateSource.VIDEO_METADATA))
        val planner = StandardizePlanner(fs, reader)

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(emptyList<Any>(), second.renames)
        assertEquals(emptyList<Any>(), second.unfixable)
    }

    @Test
    fun `plan leaves a renamed photo with no metadata alone and does not call it unfixable`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_2016-08-28_19-30-34.jpg")

        val plan = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root)

        assertEquals(emptyList<Any>(), plan.renames)
        assertEquals(emptyList<Any>(), plan.unfixable)
    }

    @Test
    fun `plan leaves a WhatsApp video alone once it is renamed`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "VID-20180314-WA0073.mp4")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(listOf("VID_2018-03-14_00-00-00.mp4"), fs.list(fs.root).map { it.name })
        assertEquals(emptyList<Any>(), second.renames)
        assertEquals(emptyList<Any>(), second.unfixable)
    }

    // Photos that all say 2017-04-25 15:26:02 in their EXIF, so their standard names collide and the planner never
    // depends on reading a date back from a name: only on leaving a collision copy where it is.
    private fun collidingPhotos(fs: FakeFileSystemRepository, vararg names: String): FakeMediaMetadataReader {
        val reader = FakeMediaMetadataReader()
        val taken = MediaDate(LocalDateTime.of(2017, 4, 25, 15, 26, 2), MediaDateSource.EXIF)
        names.forEach { reader.setCaptureDateTime(fs.addFile(fs.root, it), taken) }
        return reader
    }

    @Test
    fun `plan leaves two files that collided alone on the next run`() {
        val fs = FakeFileSystemRepository()
        val planner = StandardizePlanner(fs, collidingPhotos(fs, "IMG_0001.jpg", "IMG_0002.jpg"))

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(
            listOf("IMG_2017-04-25_15-26-02.jpg", "IMG_2017-04-25_15-26-02__dup2.jpg"),
            fs.list(fs.root).map { it.name }.sorted(),
        )
        assertEquals(emptyList<Any>(), second.renames)
        assertEquals(emptyList<Any>(), second.unfixable)
    }

    @Test
    fun `plan leaves two files with no metadata that collided alone on the next run`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_20170425_152602.jpg")
        fs.addFile(fs.root, "IMG_20170425_152602_1.jpg")
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(emptyList<Any>(), second.renames)
        assertEquals(emptyList<Any>(), second.unfixable)
    }

    @Test
    fun `plan leaves three files that collided alone on the next run`() {
        val fs = FakeFileSystemRepository()
        val planner = StandardizePlanner(fs, collidingPhotos(fs, "IMG_0001.jpg", "IMG_0002.jpg", "IMG_0003.jpg"))

        planner.apply(planner.plan(fs.root))
        val second = planner.plan(fs.root)

        assertEquals(
            listOf(
                "IMG_2017-04-25_15-26-02.jpg",
                "IMG_2017-04-25_15-26-02__dup2.jpg",
                "IMG_2017-04-25_15-26-02__dup3.jpg",
            ),
            fs.list(fs.root).map { it.name }.sorted(),
        )
        assertEquals(emptyList<Any>(), second.renames)
    }

    @Test
    fun `plan renames a collision copy whose own date is another one`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val copy = fs.addFile(fs.root, "IMG_2017-04-25_15-26-02__dup2.jpg")
        reader.setCaptureDateTime(copy, MediaDate(LocalDateTime.of(2018, 1, 1, 10, 0, 0), MediaDateSource.EXIF))

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        assertEquals("IMG_2018-01-01_10-00-00.jpg", rename.targetName)
    }

    @Test
    fun `plan renames a name whose collision number is no number`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "IMG_2017-04-25_15-26-02__dupX.jpg")
        fs.addFile(fs.root, "IMG_2018-01-01_10-00-00__dup.jpg")

        val renames = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root).renames

        assertEquals(
            setOf("IMG_2017-04-25_15-26-02.jpg", "IMG_2018-01-01_10-00-00.jpg"),
            renames.map { it.targetName }.toSet(),
        )
    }

    @Test
    fun `plan renames a file that only contains a collision name`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val file = fs.addFile(fs.root, "holiday_IMG_2017-04-25_15-26-02__dup2.jpg")
        reader.setCaptureDateTime(file, MediaDate(LocalDateTime.of(2017, 4, 25, 15, 26, 2), MediaDateSource.EXIF))

        val rename = StandardizePlanner(fs, reader).plan(fs.root).renames.single()

        assertEquals("IMG_2017-04-25_15-26-02.jpg", rename.targetName)
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

        val plan = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(
            fs.root,
            RecordingJobControl(stopAfterReports = 1),
        )

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

    @Test
    fun `plan renames a heif like any other photo`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "download_20160828_193034.heif", "not a real heif".toByteArray())

        val rename = StandardizePlanner(fs, FakeMediaMetadataReader()).plan(fs.root).renames.single()

        assertEquals("IMG_2016-08-28_19-30-34.heif", rename.targetName)
    }

    @Test
    fun `apply records each rename with the name, size and dates before and after`() {
        val fs = FakeFileSystemRepository()
        val photo = fs.addFile(fs.root, "download_20160828_193034.jpeg", ByteArray(7))
        fs.writeTimestamps(photo, FileTimestamps(created = DAY, modified = DAY))
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val control = RecordingJobControl()

        planner.apply(planner.plan(fs.root), control)

        assertEquals(
            listOf(
                FileRecord(
                    "download_20160828_193034.jpeg",
                    FileResult.Done,
                    before = FileState(7, DAY, DAY, name = "download_20160828_193034.jpeg"),
                    after = FileState(7, DAY, DAY, name = "IMG_2016-08-28_19-30-34.jpeg"),
                ),
            ),
            control.files,
        )
    }

    @Test
    fun `a rename that fails is recorded and the next file is still renamed`() {
        val inner = FakeFileSystemRepository()
        inner.addFile(inner.root, "download_20160828_193034.jpeg", "x".toByteArray())
        inner.addFile(inner.root, "download_20160901_100000.jpeg", "x".toByteArray())
        val fs = FirstRenameFails(inner)
        val planner = StandardizePlanner(fs, FakeMediaMetadataReader())
        val control = RecordingJobControl()

        val renamed = planner.apply(planner.plan(inner.root), control)

        assertEquals(1, renamed)
        assertEquals(listOf(FileResult.Failed, FileResult.Done), control.files.map { it.result })
        assertEquals("locked", control.files.first().message)
        val names = fs.list(inner.root).map { it.name }
        assertTrue(control.files.first().path in names)
        assertEquals(1, names.count { it.startsWith("IMG_") })
    }
}
