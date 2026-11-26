package app.osprey.desktop

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.reorg.DuplicateChoice
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgResult
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.NoJobControl
import app.osprey.settings.InputMode
import java.io.IOException
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
class EndToEndRealTest : TimedTest() {

    private val fs = DesktopFileSystemRepository()
    private val mediaMetadataReader = DesktopMediaMetadataReader()
    private lateinit var libraryDir: Path
    private lateinit var yearDir: Path
    private val trashedFile: Path
        get() = libraryDir.resolve("Trash/2015/Dedupe/IMG_20150101_000000.jpg")

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
    fun `rename strips a copy suffix in capitals from a real file`() {
        Files.write(yearDir.resolve("photo - COPY.jpg"), "a".toByteArray())
        val planner = RenamePlanner(fs)

        planner.apply(planner.plan(PathHandle(yearDir)))

        assertEquals(listOf("photo.jpg"), fs.list(PathHandle(yearDir)).map { it.name })
    }

    @Test
    fun `rename strips a French copy suffix from a real file`() {
        Files.write(yearDir.resolve("photo - Copie.jpg"), "a".toByteArray())
        val planner = RenamePlanner(fs)

        planner.apply(planner.plan(PathHandle(yearDir)))

        assertEquals(listOf("photo.jpg"), fs.list(PathHandle(yearDir)).map { it.name })
    }

    @Test
    fun `reorg gathers inputs and refiles the output by filename date, and a second run plans nothing`() {
        // Output (the library): a misfiled image, an undated one, and a Trash folder that must never be entered.
        Files.write(yearDir.resolve("IMG_20190101_000100.jpg"), "b".toByteArray())
        Files.write(yearDir.resolve("mystery.jpg"), "c".toByteArray())
        Files.createDirectories(libraryDir.resolve("Trash").resolve("2015").resolve("Dedupe"))
        Files.write(trashedFile, "e".toByteArray())
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
        assertTrue(Files.exists(trashedFile))
        assertEquals(emptyList<Any>(), planner.plan(output, listOf(sources[0])).items)
    }

    /** The real file system, except that deleting [name] fails: a Move that stopped after its verified copy. */
    private class FailsToRemove(private val inner: FileSystemRepository, private val name: String) :
        FileSystemRepository by inner {

        override fun delete(entry: FileHandle) {
            if ((entry as PathHandle).path.fileName.toString() == name) throw IOException("interrupted")
            inner.delete(entry)
        }
    }

    private val libraryName = "IMG_20180101_000000.jpg"
    private val inputName = "IMG_20180101_000001.jpg"

    private fun moveFrom(camera: Path) = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))

    /** A Move input file with the same bytes as a library file, planned for the user to settle. */
    private fun settled(keepInput: Boolean, keepLibrary: Boolean): Pair<Path, ReorgResult> {
        val camera = Files.createDirectory(tempInput("Camera"))
        Files.write(Files.createDirectory(libraryDir.resolve("2018")).resolve(libraryName), "a".toByteArray())
        Files.write(camera.resolve(inputName), "a".toByteArray())
        val output = PathHandle(libraryDir)
        val planner = ReorgPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(output, moveFrom(camera))
        val choices = plan.duplicates.map { DuplicateChoice(it, keepInput, keepLibrary) }
        return camera to planner.apply(plan, output, choices, NoJobControl)
    }

    private fun reorgTrash(): Path = libraryDir.resolve("Trash").resolve("2018").resolve("Reorg")

    @Test
    fun `a Move interrupted after the check is offered again and setting the input aside cleans the folder`() {
        val camera = Files.createDirectory(tempInput("Camera"))
        Files.write(camera.resolve(inputName), "a".toByteArray())
        val output = PathHandle(libraryDir)
        val interrupted = ReorgPlanner(FailsToRemove(fs, inputName), mediaMetadataReader)
        val first = interrupted.apply(interrupted.plan(output, moveFrom(camera)), output)
        assertEquals(1, first.failures.size)
        assertTrue(Files.exists(camera.resolve(inputName)))
        assertTrue(Files.exists(libraryDir.resolve("2018").resolve(inputName)))

        val planner = ReorgPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(output, moveFrom(camera))
        assertEquals(emptyList<Any>(), plan.items)
        val pair = plan.duplicates.single()
        assertEquals("Camera/$inputName", pair.input.label)
        assertEquals("2018/$inputName", pair.library.label)
        val result = planner.apply(plan, output, listOf(DuplicateChoice(pair, false, true)), NoJobControl)

        assertEquals(1, result.trashed)
        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(0, Files.list(camera).use { it.count() }.toInt())
        assertEquals("a", Files.readString(libraryDir.resolve("2018").resolve(inputName)))
        assertTrue(Files.exists(reorgTrash().resolve(inputName)))
        val again = planner.plan(output, moveFrom(camera))
        assertEquals(emptyList<Any>(), again.items + again.duplicates)
    }

    @Test
    fun `both files ticked change nothing on disk`() {
        val (camera, result) = settled(keepInput = true, keepLibrary = true)

        assertEquals(0, result.trashed)
        assertTrue(Files.exists(camera.resolve(inputName)))
        assertTrue(Files.exists(libraryDir.resolve("2018").resolve(libraryName)))
        assertFalse(Files.exists(libraryDir.resolve("Trash")))
    }

    @Test
    fun `an unticked input file goes to the Trash and the library file stays`() {
        val (camera, result) = settled(keepInput = false, keepLibrary = true)

        assertEquals(1, result.trashed)
        assertFalse(Files.exists(camera.resolve(inputName)))
        assertTrue(Files.exists(libraryDir.resolve("2018").resolve(libraryName)))
        assertTrue(Files.exists(reorgTrash().resolve(inputName)))
    }

    @Test
    fun `an unticked library file goes to the Trash and the input file stays`() {
        val (camera, result) = settled(keepInput = true, keepLibrary = false)

        assertEquals(1, result.trashed)
        assertTrue(Files.exists(camera.resolve(inputName)))
        assertFalse(Files.exists(libraryDir.resolve("2018").resolve(libraryName)))
        assertTrue(Files.exists(reorgTrash().resolve(libraryName)))
    }

    @Test
    fun `two unticked files both go to the Trash`() {
        val (camera, result) = settled(keepInput = false, keepLibrary = false)

        assertEquals(2, result.trashed)
        assertFalse(Files.exists(camera.resolve(inputName)))
        assertEquals(
            setOf(inputName, libraryName),
            Files.list(reorgTrash()).use { l -> l.map { it.fileName.toString() }.toList().toSet() },
        )
    }

    @Test
    fun `a file set aside comes back from the Trash screen byte for byte`() {
        settled(keepInput = false, keepLibrary = true)
        val output = PathHandle(libraryDir)
        val entries = TrashScanner(fs).scan(output).entries("2018", TrashAction.REORG)
        val restorer = TrashRestorer(fs)

        val restore = restorer.restore(output, restorer.plan(output, entries))

        assertEquals(1, restore.restored)
        assertEquals("a", Files.readString(libraryDir.resolve("2018").resolve(inputName)))
        assertTrue(Files.exists(libraryDir.resolve("2018").resolve(libraryName)))
        assertFalse("the emptied year folder is pruned", Files.exists(libraryDir.resolve("Trash").resolve("2018")))
    }

    @Test
    fun `a Copy input with the same content as a library file is still skipped without a question`() {
        val camera = Files.createDirectory(tempInput("Camera"))
        Files.write(Files.createDirectory(libraryDir.resolve("2018")).resolve(libraryName), "a".toByteArray())
        Files.write(camera.resolve(inputName), "a".toByteArray())

        val plan = ReorgPlanner(fs, mediaMetadataReader).plan(
            PathHandle(libraryDir), listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.COPY)),
        )

        assertEquals(emptyList<Any>(), plan.duplicates)
        assertEquals(listOf("Camera/$inputName"), plan.alreadyInOutput)
    }

    private fun tempInput(name: String): Path = libraryDir.resolveSibling("${libraryDir.fileName}-$name")
}
