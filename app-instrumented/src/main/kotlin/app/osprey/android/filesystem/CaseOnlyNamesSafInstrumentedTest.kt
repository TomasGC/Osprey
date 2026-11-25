package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.actions.visual.moveUncheckedToTrash
import app.osprey.android.media.AndroidMediaMetadataReader
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.JobControl
import app.osprey.settings.InputMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val STANDARD = "IMG_2019-12-31_23-59-30"

/** Keeps the files a run recorded as failed. */
private class Failures : JobControl {
    val failed = mutableListOf<String>()
    override fun shouldStop(): Boolean = false
    override fun report(done: Int, total: Int, current: String) = Unit
    override fun record(file: FileRecord) {
        if (file.result == FileResult.Failed) failed += "${file.path}: ${file.message}"
    }
    override val failedFiles: Int get() = failed.size
}

/**
 * Names that differ only by case on the real shared storage (#132), which takes them for one name and, asked for a
 * taken one, picks `name (1)` and reports success. Every action must give the names it planned and keep every file.
 */
@RunWith(AndroidJUnit4::class)
class CaseOnlyNamesSafInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = AndroidFileSystemRepository(context)
    private val reader = AndroidMediaMetadataReader(context)
    private val control = Failures()
    private var testRoot: DocumentFile? = null
    private val root: FileHandle get() = AndroidFileHandle(testRoot!!)

    @Before
    fun setUp() {
        testRoot = SafGrant.workFolder(context, "osprey_case_names_test")
        assumeTrue(SafGrant.SKIP_REASON, testRoot != null)
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    /** Creates the file at [path] under the test folder, with its folders. */
    private fun file(path: String, content: String = path): FileHandle {
        var folder = testRoot!!
        val segments = path.split('/')
        for (name in segments.dropLast(1)) {
            folder = folder.findFile(name) ?: folder.createDirectory(name)!!
        }
        val doc = folder.createFile("application/octet-stream", segments.last())!!
        context.contentResolver.openOutputStream(doc.uri)!!.use { it.write(content.toByteArray()) }
        return AndroidFileHandle(doc)
    }

    private fun folder(name: String): FileHandle = AndroidFileHandle(testRoot!!.findFile(name)!!)

    /** Every file under the test folder as `path=content`, so a lost, stray or renamed file shows. */
    private fun tree(folder: FileHandle = root, prefix: String = ""): Set<String> = fs.list(folder).flatMap {
        if (it.isDirectory) {
            tree(it.handle, "$prefix${it.name}/")
        } else {
            listOf("$prefix${it.name}=${String(fs.readBytes(it.handle))}")
        }
    }.toSet()

    private fun folders(): Set<String> = fs.list(root).filter { it.isDirectory }.map { it.name }.toSet()

    @Test
    fun renameToItsOwnNameInAnotherCaseChangesOnlyTheCase() {
        val entry = file("img_a.jpg", "one")

        fs.rename(entry, "IMG_a.jpg")

        assertEquals(setOf("IMG_a.jpg=one"), tree())
    }

    @Test
    fun renameOntoAnotherFileInAnotherCaseIsRefused() {
        val entry = file("a.jpg", "one")
        file("b.jpg", "two")

        val refusal = assertThrows(FileAlreadyExistsException::class.java) { fs.rename(entry, "B.JPG") }

        assertEquals("B.JPG already exists", refusal.message)
        assertEquals(setOf("a.jpg=one", "b.jpg=two"), tree())
    }

    @Test
    fun moveAndCopyOntoANameInAnotherCaseAreRefusedAndLeaveNoStrayFile() {
        file("A.JPG", "kept")
        val entry = file("sub/a.jpg", "source")

        assertThrows(FileAlreadyExistsException::class.java) { fs.move(entry, root) }
        val refusal = assertThrows(FileAlreadyExistsException::class.java) { fs.copyTo(entry, root) }

        assertEquals("a.jpg already exists in ${testRoot!!.name}", refusal.message)

        assertEquals(setOf("A.JPG=kept", "sub/a.jpg=source"), tree())
    }

    @Test
    fun moveUnderANewNameOntoAnotherCaseKeepsTheSourceName() {
        file("sub/B.JPG", "kept")
        val entry = file("a.jpg", "source")

        assertThrows(FileAlreadyExistsException::class.java) { fs.move(entry, folder("sub"), "b.jpg") }

        assertEquals(setOf("sub/B.JPG=kept", "a.jpg=source"), tree())
    }

    @Test
    fun moveUnderANameTheSourceFolderHasInAnotherCaseGivesThatName() {
        file("B.JPG", "neighbour")
        file("sub/other.txt", "other")
        val entry = file("a.jpg", "source")

        val moved = fs.move(entry, folder("sub"), "b.jpg")

        assertEquals(setOf("B.JPG=neighbour", "sub/other.txt=other", "sub/b.jpg=source"), tree())
        assertEquals("source", String(fs.readBytes(moved)))
    }

    @Test
    fun moveUnderANameTheSourceFolderHasExactlyGivesThatName() {
        file("b.jpg", "neighbour")
        file("sub/other.txt", "other")
        val entry = file("a.jpg", "source")

        fs.move(entry, folder("sub"), "b.jpg")

        assertEquals(setOf("b.jpg=neighbour", "sub/other.txt=other", "sub/b.jpg=source"), tree())
    }

    @Test
    fun moveToAnotherFolderCanChangeOnlyTheCaseOfTheName() {
        file("sub/other.txt", "other")
        val entry = file("a.jpg", "source")

        fs.move(entry, folder("sub"), "A.JPG")

        assertEquals(setOf("sub/other.txt=other", "sub/A.JPG=source"), tree())
    }

    @Test
    fun visualDupesNumbersAFileWhoseNameIsInTheTrashInAnotherCase() {
        file("Trash/2019/VisualDupes/B.JPG", "old")
        val keep = file("2019/a.jpg", "keep")
        val dupe = file("2019/b.jpg", "dupe")
        val group = listOf(VisualDupeMember(keep, "a.jpg", 4), VisualDupeMember(dupe, "b.jpg", 4))

        val result = moveUncheckedToTrash(
            fs,
            TrashLocation(root, "2019"),
            folder("2019"),
            group,
            setOf("a.jpg"),
            control,
        )

        assertEquals(emptyList<String>(), result.failed)
        assertEquals(
            setOf("Trash/2019/VisualDupes/B.JPG=old", "Trash/2019/VisualDupes/b__dup1.jpg=dupe", "2019/a.jpg=keep"),
            tree(),
        )
    }

    @Test
    fun createFolderUsesAFolderOfAnotherCase() {
        file("trash/old.jpg", "old")

        fs.createFolder(root, "Trash")

        assertEquals(setOf("trash"), folders())
    }

    @Test
    fun standardizeKeepsBothOfTwoFilesWhoseNamesWouldDifferOnlyByCase() {
        file("download_20191231_235930.PNG", "one")
        file("20191231_235930.png", "two")
        val planner = StandardizePlanner(fs, reader)

        planner.apply(planner.plan(root), control)

        assertEquals(emptyList<String>(), control.failed)
        assertEquals(setOf("one", "two"), tree().map { it.substringAfter('=') }.toSet())
        val names = tree().map { it.substringBefore('=').substringBeforeLast('.') }.toSet()
        assertEquals(setOf(STANDARD, "${STANDARD}__dup2"), names)
        assertEquals(emptyList<Any>(), planner.plan(root).renames)
    }

    @Test
    fun standardizeRenamesAFileWhoseOwnNameDiffersOnlyByCase() {
        file("img_2019-12-31_23-59-30.png", "one")
        val planner = StandardizePlanner(fs, reader)

        planner.apply(planner.plan(root), control)

        assertEquals(emptyList<String>(), control.failed)
        assertEquals(setOf("$STANDARD.png=one"), tree())
    }

    @Test
    fun flattenKeepsEveryFile() {
        file("06/a.jpg", "one")
        file("07/A.JPG", "two")
        file("b.jpg", "three")
        file("06/B.JPG", "four")
        val planner = FlattenPlanner(fs)

        planner.apply(planner.plan(root), root, control) {}

        assertEquals(emptyList<String>(), control.failed)
        assertEquals(setOf("one", "two", "three", "four"), tree().map { it.substringAfter('=') }.toSet())
        assertEquals(emptySet<String>(), folders())
        assertEquals(4, tree().map { it.substringBefore('=').lowercase() }.toSet().size)
    }

    @Test
    fun renameSkipsACleanNameTakenInAnotherCase() {
        file("photo.JPG", "keep")
        file("photo (1).jpg", "copy")
        val planner = RenamePlanner(fs)

        planner.apply(planner.plan(root), control)

        assertEquals(emptyList<String>(), control.failed)
        assertEquals(setOf("photo.JPG=keep", "photo (1).jpg=copy"), tree())
    }

    @Test
    fun dedupeUsesALowerCaseTrashFolderAndKeepsWhatItHolds() {
        file("trash/2019/Dedupe/BB.JPG", "old")
        file("2019/a.jpg", "same")
        file("2019/bb.jpg", "same")
        val planner = DedupePlanner(fs)
        val location = TrashLocation(root, "2019")

        planner.apply(planner.plan(folder("2019"), location), location, control)

        assertEquals(emptyList<String>(), control.failed)
        assertEquals(setOf("trash", "2019"), folders())
        assertEquals(
            setOf("trash/2019/Dedupe/BB.JPG=old", "trash/2019/Dedupe/bb__dup1.jpg=same", "2019/a.jpg=same"),
            tree(),
        )
    }

    @Test
    fun reorgKeepsBothOfTwoNamesThatDifferOnlyByCase() {
        file("Library/2019/IMG_20191231_235930.JPG", "library")
        file("Camera/IMG_20191231_235930.jpg", "input")
        val planner = ReorgPlanner(fs, reader)
        val output = folder("Library")

        val plan = planner.plan(output, listOf(ReorgSource(folder("Camera"), "Camera", InputMode.MOVE)))
        val result = planner.apply(plan, output, control)

        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(
            setOf(
                "Library/2019/IMG_20191231_235930.JPG=library",
                "Library/2019/IMG_20191231_235930__year1.jpg=input",
            ),
            tree(),
        )
    }

    @Test
    fun restoreReplacesALibraryFileOfAnotherCaseAndKeepsIt() {
        file("2019/A.JPG", "library")
        file("Trash/2019/Dedupe/a.jpg", "trashed")
        val restorer = TrashRestorer(fs)

        val plan = restorer.plan(root, TrashScanner(fs).scan(root).entries)
        assertNull(restorer.resolve(root, plan.conflicts.single(), ConflictChoice.TRASH_COPY))

        assertEquals(setOf("2019/a.jpg=trashed", "Trash/2019/Replaced/A.JPG=library"), tree())
    }
}
