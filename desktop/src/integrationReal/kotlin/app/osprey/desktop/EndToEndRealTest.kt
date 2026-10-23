package app.osprey.desktop

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.settings.InputMode
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
    private lateinit var libraryDir: Path
    private lateinit var yearDir: Path

    @Before
    fun setUp() {
        libraryDir = Files.createTempDirectory("osprey-e2e-real-test")
        yearDir = Files.createDirectory(libraryDir.resolve("2016"))
    }

    @After
    fun tearDown() {
        libraryDir.toFile().deleteRecursively()
        listOf("Camera", "WhatsApp").forEach { tempInput(it).toFile().deleteRecursively() }
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
        // because "photo.jpg" was already claimed) moves into Trash/2016/Dedupe.
        val dedupePlanner = DedupePlanner(fs)
        val trash = TrashLocation(PathHandle(libraryDir), "2016")
        val dedupePlan = dedupePlanner.plan(root, trash)
        dedupePlanner.apply(dedupePlan, trash)
        assertEquals(1, dedupePlan.moves.size)
        assertTrue(Files.exists(libraryDir.resolve("Trash").resolve("2016").resolve("Dedupe").resolve("photo (3).jpg")))
        assertFalse(Files.exists(yearDir.resolve("photo (3).jpg")))

        // Standardize: the filename-dateable file gets its real IMG_ name; the trashed
        // duplicate is never touched, since it lives outside the year folder.
        val standardizePlanner = StandardizePlanner(fs, mediaMetadataReader)
        val standardizePlan = standardizePlanner.plan(root)
        standardizePlanner.apply(standardizePlan)
        assertTrue(Files.exists(yearDir.resolve("IMG_2016-08-28_19-30-34.jpeg")))

        // Final real directory state: exactly the two surviving files.
        val finalNames = fs.list(root).map { it.name }.toSet()
        assertEquals(setOf("photo.jpg", "IMG_2016-08-28_19-30-34.jpeg"), finalNames)
    }

    @Test
    fun `reorg gathers inputs and refiles the output by filename date, and a second run plans nothing`() {
        // Output (the library): a misfiled image, an undated one, and a TODELETE that must never be entered.
        Files.write(yearDir.resolve("IMG_20190101_000100.jpg"), "b".toByteArray())
        Files.write(yearDir.resolve("mystery.jpg"), "c".toByteArray())
        Files.createDirectory(yearDir.resolve("TODELETE"))
        Files.write(yearDir.resolve("TODELETE").resolve("IMG_20150101_000000.jpg"), "e".toByteArray())
        // Two inputs: one copied, one moved; the second holds a duplicate of the first's content.
        val camera = Files.createDirectory(tempInput("Camera"))
        val whatsapp = Files.createDirectory(tempInput("WhatsApp"))
        Files.write(camera.resolve("IMG_20160828_193034.jpg"), "a".toByteArray())
        Files.write(whatsapp.resolve("IMG_20170425_152602.jpg"), "d".toByteArray())
        Files.write(whatsapp.resolve("IMG_20160828_193034_copy.jpg"), "a".toByteArray())

        val output = PathHandle(libraryDir)
        val sources = listOf(
            ReorgSource(PathHandle(camera), "Camera", InputMode.COPY),
            ReorgSource(PathHandle(whatsapp), "WhatsApp", InputMode.MOVE),
        )
        val planner = ReorgPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(output, sources)
        val result = planner.apply(plan, output)

        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(3, plan.items.size)
        assertEquals(1, plan.alreadyInOutput.size)
        assertTrue(Files.exists(libraryDir.resolve("2016").resolve("IMG_20160828_193034.jpg")))
        assertTrue(Files.exists(camera.resolve("IMG_20160828_193034.jpg")))
        assertTrue(Files.exists(libraryDir.resolve("2017").resolve("IMG_20170425_152602.jpg")))
        assertFalse(Files.exists(whatsapp.resolve("IMG_20170425_152602.jpg")))
        assertTrue(Files.exists(libraryDir.resolve("2019").resolve("IMG_20190101_000100.jpg")))
        assertTrue(Files.exists(yearDir.resolve("mystery.jpg")))
        assertTrue(Files.exists(yearDir.resolve("TODELETE").resolve("IMG_20150101_000000.jpg")))
        assertEquals(emptyList<Any>(), planner.plan(output, listOf(sources[0])).items)
    }

    private fun tempInput(name: String): Path = libraryDir.resolveSibling("${libraryDir.fileName}-$name")

    @Test
    fun `trash migration moves old TODELETE folders into Trash and a second run plans nothing`() {
        Files.createDirectory(yearDir.resolve("TODELETE"))
        Files.write(yearDir.resolve("TODELETE").resolve("a.jpg"), "a".toByteArray())
        Files.createDirectory(yearDir.resolve("TODELETEDUPS"))
        Files.write(yearDir.resolve("TODELETEDUPS").resolve("b.jpg"), "b".toByteArray())
        Files.write(yearDir.resolve("keep.jpg"), "k".toByteArray())

        val root = PathHandle(libraryDir)
        val planner = TrashMigrationPlanner(fs)
        assertTrue(planner.hasLegacy(root))
        planner.apply(planner.plan(root), root) {}

        assertTrue(Files.exists(libraryDir.resolve("Trash").resolve("2016").resolve("Dedupe").resolve("a.jpg")))
        assertTrue(Files.exists(libraryDir.resolve("Trash").resolve("2016").resolve("VisualDupes").resolve("b.jpg")))
        assertFalse(Files.exists(yearDir.resolve("TODELETE")))
        assertFalse(Files.exists(yearDir.resolve("TODELETEDUPS")))
        assertTrue(Files.exists(yearDir.resolve("keep.jpg")))
        assertFalse(planner.hasLegacy(root))
        assertEquals(emptyList<Any>(), planner.plan(root).moves)
    }
}
