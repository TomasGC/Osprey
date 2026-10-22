package app.osprey.desktop

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.yearfolders.YearFoldersPlanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs every applicable `:core` planner (#3-#7) against the real [DesktopFileSystemRepository]/
 * [DesktopMediaMetadataReader] on a real, messy year folder -- the spot-check #10's DoD asks
 * for, beyond each planner's own unit-level tests against the in-memory fake.
 */
class EndToEndRealTest {

    private val fs = DesktopFileSystemRepository()
    private val mediaMetadataReader = DesktopMediaMetadataReader()
    private lateinit var yearDir: Path

    @Before
    fun setUp() {
        yearDir = Files.createTempDirectory("osprey-e2e-real-test")
    }

    @After
    fun tearDown() {
        yearDir.toFile().deleteRecursively()
    }

    @Test
    fun `flatten, rename, dedupe and standardize run end-to-end against real files`() {
        // A messy year folder: a nested month folder, sync-suffix noise, a byte-identical
        // duplicate, and a filename-dateable image with no EXIF -- one real file per concern.
        val monthDir = Files.createDirectory(yearDir.resolve("06"))
        Files.write(monthDir.resolve("download_20160828_193034.jpeg"), "nested original".toByteArray())
        Files.write(yearDir.resolve("photo (2).jpg"), "dup content".toByteArray())
        Files.write(yearDir.resolve("photo (3).jpg"), "dup content".toByteArray())

        val root = PathHandle(yearDir)

        // Flatten: the nested file moves up to the year folder.
        val flattenPlanner = FlattenPlanner(fs)
        flattenPlanner.apply(flattenPlanner.plan(root), root) {}
        assertTrue(Files.exists(yearDir.resolve("download_20160828_193034.jpeg")))
        assertFalse(Files.exists(monthDir))

        // Rename: the " (2)"/" (3)" sync-copy suffixes are stripped where they don't collide.
        val renamePlanner = RenamePlanner(fs)
        val renamePlan = renamePlanner.plan(root)
        renamePlanner.apply(renamePlan)
        assertEquals(1, renamePlan.renames.size)
        assertEquals(1, renamePlan.skips.size)
        assertTrue(Files.exists(yearDir.resolve("photo.jpg")))

        // Dedupe: the byte-identical duplicate (still named "photo (3).jpg", skipped by rename
        // because "photo.jpg" was already claimed) moves into TODELETE.
        val dedupePlanner = DedupePlanner(fs)
        val dedupePlan = dedupePlanner.plan(root)
        dedupePlanner.apply(dedupePlan, root)
        assertEquals(1, dedupePlan.moves.size)
        assertTrue(Files.exists(yearDir.resolve("TODELETE").resolve("photo (3).jpg")))
        assertFalse(Files.exists(yearDir.resolve("photo (3).jpg")))

        // Standardize: the filename-dateable file gets its real IMG_ name; the TODELETE
        // duplicate is never touched, since it's a subfolder, not a file.
        val standardizePlanner = StandardizePlanner(fs, mediaMetadataReader)
        val standardizePlan = standardizePlanner.plan(root)
        standardizePlanner.apply(standardizePlan)
        assertTrue(Files.exists(yearDir.resolve("IMG_2016-08-28_19-30-34.jpeg")))

        // Final real directory state: exactly the two surviving files, plus TODELETE.
        val finalNames = fs.list(root).map { it.name }.toSet()
        assertEquals(setOf("photo.jpg", "IMG_2016-08-28_19-30-34.jpeg", "TODELETE"), finalNames)
    }

    @Test
    fun `year folders groups a flat library by filename date and a second run plans nothing`() {
        // A flat library root: two filename-dateable images from different years, an undated one,
        // a misfiled one inside the wrong year folder, and a TODELETE that must never be entered.
        Files.write(yearDir.resolve("IMG_20160828_193034.jpg"), "a".toByteArray())
        Files.write(yearDir.resolve("IMG_20190101_000100.jpg"), "b".toByteArray())
        Files.write(yearDir.resolve("mystery.jpg"), "c".toByteArray())
        Files.createDirectory(yearDir.resolve("2019"))
        Files.write(yearDir.resolve("2019").resolve("IMG_20170425_152602.jpg"), "d".toByteArray())
        Files.createDirectory(yearDir.resolve("TODELETE"))
        Files.write(yearDir.resolve("TODELETE").resolve("IMG_20150101_000000.jpg"), "e".toByteArray())

        val root = PathHandle(yearDir)
        val planner = YearFoldersPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(root)
        planner.apply(plan, root)

        assertEquals(3, plan.moves.size)
        assertEquals(listOf("mystery.jpg"), plan.undated.map { it.name })
        assertTrue(Files.exists(yearDir.resolve("2016").resolve("IMG_20160828_193034.jpg")))
        assertTrue(Files.exists(yearDir.resolve("2019").resolve("IMG_20190101_000100.jpg")))
        assertTrue(Files.exists(yearDir.resolve("2017").resolve("IMG_20170425_152602.jpg")))
        assertTrue(Files.exists(yearDir.resolve("mystery.jpg")))
        assertTrue(Files.exists(yearDir.resolve("TODELETE").resolve("IMG_20150101_000000.jpg")))
        assertEquals(emptyList<Any>(), planner.plan(root).moves)
    }
}
