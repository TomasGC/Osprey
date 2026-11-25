package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.testing.fileTree
import app.osprey.desktop.testing.ignoresCase
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.history.FileResult
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val SHOT = LocalDateTime.of(2019, 12, 31, 23, 59, 30)
private const val STANDARD = "IMG_2019-12-31_23-59-30"

/**
 * Every action on real files whose names differ only by case (#132), through its real controller, job queue and
 * history. The folder is probed: on a case-insensitive file system (Windows) a collision the planner misses is a
 * failed or lost file; on a case-sensitive one (Linux) nothing collides. The planners must give the same names on both
 * (they compare ignoring case everywhere, so a library stays portable); only what the repository refuses depends on
 * the probe.
 */
class CaseOnlyNamesRealTest : TimedTest() {

    private val harness = ActionHarness()

    @After
    fun tearDown() = harness.delete()

    private fun tree(): Set<String> = fileTree(harness.library)

    private fun libraryFile(relative: String, content: String): Path {
        val path = harness.library.resolve(relative)
        Files.createDirectories(path.parent)
        return Files.writeString(path, content)
    }

    private fun shot(path: Path) =
        harness.reader.setCaptureDateTime(PathHandle(path), MediaDate(SHOT, MediaDateSource.EXIF))

    private fun assertNoFailedFile() {
        val failed = harness.runsAfterRestart().flatMap { it.files }.filter { it.result == FileResult.Failed }
        assertEquals(emptyList<Any>(), failed.map { "${it.path}: ${it.message}" })
    }

    private fun standardize() {
        val launch = harness.launch()
        launch.scanAndApply(standardizeController(harness.fs, harness.reader, launch.jobs) { harness.libraryLabel })
    }

    // Without this, a Windows run on a folder that kept case would pass while proving nothing about it.
    @Test
    fun `the folder is probed, and the file system refuses a name of another case exactly where it is taken`() {
        val ignores = ignoresCase(harness.root)
        if (System.getProperty("os.name").startsWith("Windows")) assertTrue("Windows folder keeps case", ignores)
        val a = PathHandle(harness.file("a.jpg", "one"))
        harness.file("b.jpg", "two")

        if (ignores) {
            assertThrows(FileAlreadyExistsException::class.java) { harness.fs.rename(a, "B.JPG") }
            assertEquals(setOf("2021/a.jpg=one", "2021/b.jpg=two"), tree())
        } else {
            harness.fs.rename(a, "B.JPG")
            assertEquals(setOf("2021/B.JPG=one", "2021/b.jpg=two"), tree())
        }
    }

    @Test
    fun `Standardize keeps both of two files whose names would differ only by case`() {
        shot(harness.file("a.PNG", "one"))
        shot(harness.file("b.png", "two"))

        standardize()

        assertNoFailedFile()
        val files = tree()
        assertEquals(setOf("one", "two"), files.map { it.substringAfter('=') }.toSet())
        val names = files.map { it.substringBefore('=').substringBeforeLast('.').lowercase() }.toSet()
        assertEquals(setOf("2021/${STANDARD.lowercase()}", "2021/${STANDARD.lowercase()}__dup2"), names)
    }

    @Test
    fun `Standardize really renames a file whose own name differs only by case`() {
        shot(harness.file("img_2019-12-31_23-59-30.png", "one"))

        standardize()

        assertNoFailedFile()
        assertEquals(setOf("2021/$STANDARD.png=one"), tree())
    }

    @Test
    fun `Flatten keeps every file of names that differ only by case`() {
        harness.file("06/a.jpg", "one")
        harness.file("07/A.JPG", "two")
        harness.file("b.jpg", "three")
        harness.file("06/B.JPG", "four")
        val launch = harness.launch()

        launch.scanAndApply(flattenController(harness.fs, launch.jobs) { harness.libraryLabel })

        assertNoFailedFile()
        // Which subfolder is listed first is the file system's choice, so either of the two may keep the name.
        val kept = setOf("2021/b.jpg=three", "2021/B__flat1.JPG=four")
        val sixthFirst = kept + setOf("2021/a.jpg=one", "2021/A__flat1.JPG=two")
        val seventhFirst = kept + setOf("2021/A.JPG=two", "2021/a__flat1.jpg=one")
        val files = tree()
        assertTrue("$files", files == sixthFirst || files == seventhFirst)
    }

    @Test
    fun `Rename skips a clean name taken in another case`() {
        harness.file("photo.JPG", "keep")
        harness.file("photo (1).jpg", "copy")
        harness.file("A (2).jpg", "two")
        harness.file("a (1).jpg", "one")
        val launch = harness.launch()

        launch.scanAndApply(renameController(harness.fs, launch.jobs) { harness.libraryLabel })

        assertNoFailedFile()
        assertEquals(
            setOf("2021/photo.JPG=keep", "2021/photo (1).jpg=copy", "2021/A.jpg=two", "2021/a (1).jpg=one"),
            tree(),
        )
    }

    @Test
    fun `Dedupe keeps what its Trash holds under another case, in a lower case trash folder`() {
        libraryFile("trash/2021/Dedupe/BB.JPG", "old")
        harness.file("a.jpg", "same")
        harness.file("bb.jpg", "same")
        val launch = harness.launch()

        launch.scanAndApply(
            dedupeController(harness.fs, launch.jobs, { TrashLocation(PathHandle(harness.library), "2021") }) {
                harness.libraryLabel
            },
        )

        assertNoFailedFile()
        assertEquals(
            setOf("trash/2021/Dedupe/BB.JPG=old", "trash/2021/Dedupe/bb__dup1.jpg=same", "2021/a.jpg=same"),
            tree(),
        )
        val listed = TrashScanner(harness.fs).scan(PathHandle(harness.library)).entries.map { it.name }.toSet()
        assertEquals("the Trash screen lists them", setOf("BB.JPG", "bb__dup1.jpg"), listed)
    }

    @Test
    fun `Reorg keeps both of two names that differ only by case`() {
        val camera = Files.createDirectory(harness.root.resolve("Camera"))
        shot(libraryFile("2019/X.JPG", "library"))
        shot(Files.writeString(camera.resolve("x.jpg"), "input"))
        val planner = ReorgPlanner(harness.fs, harness.reader)
        val output = PathHandle(harness.library)

        val plan = planner.plan(output, listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE)))
        val result = planner.apply(plan, output)

        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(setOf("2019/X.JPG=library", "2019/x__year1.jpg=input"), tree())
    }

    @Test
    fun `a restore onto a name in another case asks, and replacing keeps the library file`() {
        libraryFile("2019/A.JPG", "library")
        libraryFile("Trash/2019/Dedupe/a.jpg", "trashed")
        val root = PathHandle(harness.library)
        val restorer = TrashRestorer(harness.fs)

        val plan = restorer.plan(root, TrashScanner(harness.fs).scan(root).entries)
        assertTrue("no direct restore onto it", plan.direct.isEmpty())
        assertNull(restorer.resolve(root, plan.conflicts.single(), ConflictChoice.TRASH_COPY))

        assertEquals(setOf("2019/a.jpg=trashed", "Trash/2019/Replaced/A.JPG=library"), tree())
    }
}
