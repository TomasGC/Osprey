package app.osprey.actions.yearfolders

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YearFoldersPlannerTest {

    private val fs = FakeFileSystemRepository()
    private val reader = FakeMediaMetadataReader()
    private val planner = YearFoldersPlanner(fs, reader)

    private fun exif(year: Int, month: Int = 6, day: Int = 15, hour: Int = 12, minute: Int = 0) =
        MediaDate(LocalDateTime.of(year, month, day, hour, minute), MediaDateSource.EXIF)

    private fun file(parent: FileHandle, name: String, date: MediaDate? = null): FileHandle {
        val handle = fs.addFile(parent, name, byteArrayOf(1))
        if (date != null) reader.setCaptureDateTime(handle, date)
        return handle
    }

    private fun names(folder: FileHandle) = fs.list(folder).map { it.name }.toSet()

    private fun folder(parent: FileHandle, name: String): FileHandle =
        fs.list(parent).single { it.isDirectory && it.name == name }.handle

    @Test
    fun `plan moves a root file to its capture year`() {
        file(fs.root, "a.jpg", exif(2018))

        val plan = planner.plan(fs.root)

        val move = plan.moves.single()
        assertEquals("a.jpg", move.sourcePath)
        assertEquals(2018, move.year)
        assertEquals("a.jpg", move.destName)
        assertEquals("", move.note)
    }

    @Test
    fun `plan moves a misfiled file to the year of its capture date`() {
        val y2019 = fs.addFolder(fs.root, "2019")
        file(y2019, "a.jpg", exif(2016))

        val move = planner.plan(fs.root).moves.single()

        assertEquals("2019/a.jpg", move.sourcePath)
        assertEquals(2016, move.year)
    }

    @Test
    fun `plan leaves a file already in the right year folder`() {
        val y2019 = fs.addFolder(fs.root, "2019")
        file(y2019, "a.jpg", exif(2019))

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), plan.moves)
        assertEquals(emptyList<Any>(), plan.undated)
    }

    @Test
    fun `plan resolves the year from the filename when there is no metadata`() {
        file(fs.root, "IMG_20160828_193034.jpg")

        val move = planner.plan(fs.root).moves.single()

        assertEquals(2016, move.year)
    }

    @Test
    fun `plan treats a pre-1970 reader date as undated`() {
        val handle = fs.addFile(fs.root, "scan.jpg", byteArrayOf(1))
        reader.setCaptureDateTime(handle, MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0), MediaDateSource.VIDEO_METADATA))

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), plan.moves)
        assertEquals(listOf("scan.jpg"), plan.undated.map { it.name })
    }

    @Test
    fun `plan keeps an undated file in place and reports it`() {
        file(fs.root, "mystery.jpg")

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), plan.moves)
        assertEquals(listOf("mystery.jpg"), plan.undated.map { it.name })
    }

    @Test
    fun `plan splits 31 December and 1 January into different years`() {
        file(fs.root, "a.jpg", exif(2020, 12, 31, 23, 59))
        file(fs.root, "b.jpg", exif(2021, 1, 1, 0, 0))

        val years = planner.plan(fs.root).moves.associate { it.destName to it.year }

        assertEquals(mapOf("a.jpg" to 2020, "b.jpg" to 2021), years)
    }

    @Test
    fun `plan never touches TODELETE, TODELETEDUPS or non-year folders`() {
        for (name in listOf("TODELETE", "TODELETEDUPS", "Trip")) {
            file(fs.addFolder(fs.root, name), "a.jpg", exif(2018))
        }

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), plan.moves)
        assertEquals(emptyList<Any>(), plan.undated)
    }

    @Test
    fun `plan ignores a file that is neither image nor video`() {
        file(fs.root, "notes_2018-05-01.txt")

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), plan.moves)
        assertEquals(emptyList<Any>(), plan.undated)
    }

    @Test
    fun `plan suffixes a collision with an existing file in the year folder`() {
        val y2018 = fs.addFolder(fs.root, "2018")
        file(y2018, "a.jpg", exif(2018))
        file(fs.root, "a.jpg", exif(2018))

        val move = planner.plan(fs.root).moves.single()

        assertEquals("a__year1.jpg", move.destName)
        assertEquals("(renamed, name collision)", move.note)
    }

    @Test
    fun `plan suffixes two files that collide with each other`() {
        val y2019 = fs.addFolder(fs.root, "2019")
        file(fs.root, "a.jpg", exif(2018))
        file(y2019, "a.jpg", exif(2018))

        val destNames = planner.plan(fs.root).moves.map { it.destName }.sorted()

        assertEquals(listOf("a.jpg", "a__year1.jpg"), destNames)
    }

    @Test
    fun `apply creates the year folder and moves the file`() {
        file(fs.root, "a.jpg", exif(2018))

        planner.apply(planner.plan(fs.root), fs.root)

        assertEquals(setOf("2018"), names(fs.root))
        assertEquals(setOf("a.jpg"), names(folder(fs.root, "2018")))
    }

    @Test
    fun `apply reuses an existing year folder and overwrites nothing`() {
        val y2018 = fs.addFolder(fs.root, "2018")
        file(y2018, "a.jpg", exif(2018))
        file(fs.root, "a.jpg", exif(2018))

        planner.apply(planner.plan(fs.root), fs.root)

        assertEquals(setOf("2018"), names(fs.root))
        assertEquals(setOf("a.jpg", "a__year1.jpg"), names(y2018))
    }

    @Test
    fun `a second run plans nothing`() {
        val y2019 = fs.addFolder(fs.root, "2019")
        file(fs.root, "a.jpg", exif(2018))
        file(y2019, "b.jpg", exif(2016))
        file(fs.root, "mystery.jpg")
        planner.apply(planner.plan(fs.root), fs.root)

        val second = planner.plan(fs.root)

        assertEquals(emptyList<YearFoldersMove>(), second.moves)
    }

    @Test
    fun `describe logs one line per move and per undated file and returns the move count`() {
        file(fs.root, "a.jpg", exif(2018))
        file(fs.root, "mystery.jpg")
        val lines = mutableListOf<String>()

        val count = planner.describe(planner.plan(fs.root), lines::add)

        assertEquals(1, count)
        assertTrue("  YEAR a.jpg -> 2018/a.jpg" in lines)
        assertTrue("  UNDATED mystery.jpg" in lines)
    }
}
