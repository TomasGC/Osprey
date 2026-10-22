package app.osprey.actions.standardize

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
}
