package app.osprey.actions.dates

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
}
