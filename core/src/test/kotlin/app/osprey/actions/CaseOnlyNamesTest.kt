package app.osprey.actions

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.reorg.DuplicateChoice
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.actions.visual.moveUncheckedToTrash
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.temporaryName
import app.osprey.jobs.RecordingJobControl
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.settings.InputMode
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val SHOT: LocalDateTime = LocalDateTime.of(2019, 12, 31, 23, 59, 30)
private const val STANDARD = "IMG_2019-12-31_23-59-30"

/** What every action does when two names differ only by case and the storage takes them for one name (#132). */
class CaseOnlyNamesTest {

    private val fs = FakeFileSystemRepository(ignoreCase = true)
    private val reader = FakeMediaMetadataReader()
    private val control = RecordingJobControl()

    private fun file(path: String, content: String = path, on: FakeFileSystemRepository = fs): FileHandle {
        var folder = on.root
        val segments = path.split('/')
        for (name in segments.dropLast(1)) {
            folder = on.list(folder).firstOrNull { it.isDirectory && it.name == name }?.handle
                ?: on.addFolder(folder, name)
        }
        return on.addFile(folder, segments.last(), content.toByteArray())
    }

    private fun shot(path: String, content: String = path, on: FakeFileSystemRepository = fs): FileHandle =
        file(path, content, on).also { reader.setCaptureDateTime(it, MediaDate(SHOT, MediaDateSource.EXIF)) }

    /** Every file under [folder] as `path=content`, so a lost or overwritten file shows. */
    private fun tree(
        folder: FileHandle = fs.root,
        prefix: String = "",
        on: FakeFileSystemRepository = fs,
    ): Set<String> =
        on.list(folder).flatMap {
            if (it.isDirectory) {
                tree(it.handle, "$prefix${it.name}/", on)
            } else {
                listOf("$prefix${it.name}=${String(on.readBytes(it.handle))}")
            }
        }.toSet()

    private fun folder(name: String): FileHandle = fs.list(fs.root).single { it.name == name }.handle

    private fun standardize(on: FakeFileSystemRepository = fs) {
        val planner = StandardizePlanner(on, reader)
        planner.apply(planner.plan(on.root), control)
        assertEquals("renames planned again", emptyList<Any>(), planner.plan(on.root).renames)
        assertEquals("failed files", 0, control.failedFiles)
    }

    @Test
    fun `Standardize numbers the second of two files whose names would differ only by case`() {
        shot("a.PNG", "one")
        shot("b.png", "two")

        standardize()

        assertEquals(setOf("$STANDARD.PNG=one", "${STANDARD}__dup2.png=two"), tree())
    }

    @Test
    fun `Standardize renames a file whose own name differs from the standard one only by case`() {
        shot("img_2019-12-31_23-59-30.png", "one")

        standardize()

        assertEquals(setOf("$STANDARD.png=one"), tree())
    }

    @Test
    fun `Standardize fixes the case of a collision copy and keeps its number`() {
        shot("$STANDARD.png", "one")
        shot("img_2019-12-31_23-59-30__dup3.png", "two")

        standardize()

        assertEquals(setOf("$STANDARD.png=one", "${STANDARD}__dup3.png=two"), tree())
    }

    @Test
    fun `Standardize leaves a name to the file that already has it in another case`() {
        shot("a.png", "one")
        shot("img_2019-12-31_23-59-30.png", "two")

        standardize()

        assertEquals(setOf("${STANDARD}__dup2.png=one", "$STANDARD.png=two"), tree())
    }

    @Test
    fun `Standardize keeps both files of a case-sensitive folder apart`() {
        val sensitive = FakeFileSystemRepository()
        shot("$STANDARD.png", "one", sensitive)
        shot("img_2019-12-31_23-59-30.png", "two", sensitive)

        standardize(sensitive)

        assertEquals(setOf("$STANDARD.png=one", "${STANDARD}__dup2.png=two"), tree(sensitive.root, on = sensitive))
    }

    // What an app killed between Android's two rename steps leaves behind.
    @Test
    fun `Standardize gives a file left under a temporary name its standard name back`() {
        file(temporaryName("$STANDARD.png"), "one")

        standardize()

        assertEquals(setOf("$STANDARD.png=one"), tree())
    }

    @Test
    fun `Flatten suffixes names that differ only by case, from two subfolders and from the root`() {
        file("06/a.jpg", "one")
        file("07/A.JPG", "two")
        file("b.jpg", "three")
        file("06/B.JPG", "four")
        val planner = FlattenPlanner(fs)

        val moved = planner.apply(planner.plan(fs.root), fs.root, control) {}

        assertEquals(3, moved)
        assertEquals(0, control.failedFiles)
        assertEquals(setOf("a.jpg=one", "A__flat1.JPG=two", "b.jpg=three", "B__flat1.JPG=four"), tree())
        assertEquals("the subfolders are gone", emptyList<String>(), fs.list(fs.root).filter { it.isDirectory })
    }

    @Test
    fun `Rename skips a clean name taken in another case, on disk or earlier in the plan`() {
        file("photo.JPG", "keep")
        file("photo (1).jpg", "copy")
        file("A (2).jpg", "two")
        file("a (1).jpg", "one")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)
        planner.apply(plan, control)

        assertEquals(listOf("a (1).jpg", "photo (1).jpg"), plan.skips.map { it.sourceName })
        assertEquals(0, control.failedFiles)
        assertEquals(setOf("photo.JPG=keep", "photo (1).jpg=copy", "A.jpg=two", "a (1).jpg=one"), tree())
    }

    private fun dedupeYear() {
        val planner = DedupePlanner(fs)
        val location = TrashLocation(fs.root, "2019")
        planner.apply(planner.plan(folder("2019"), location), location, control)
    }

    @Test
    fun `Dedupe numbers a file whose name is in the Trash in another case`() {
        file("Trash/2019/Dedupe/BB.JPG", "old")
        file("2019/a.jpg", "same")
        file("2019/bb.jpg", "same")

        dedupeYear()

        assertEquals(0, control.failedFiles)
        assertEquals(
            setOf("Trash/2019/Dedupe/BB.JPG=old", "Trash/2019/Dedupe/bb__dup1.jpg=same", "2019/a.jpg=same"),
            tree(),
        )
    }

    @Test
    fun `Dedupe uses Trash folders whose names differ only by case, and sees what they hold`() {
        file("trash/2019/dedupe/bb.jpg", "old")
        file("2019/a.jpg", "same")
        file("2019/bb.jpg", "same")

        dedupeYear()

        assertEquals(0, control.failedFiles)
        assertEquals(
            setOf("trash/2019/dedupe/bb.jpg=old", "trash/2019/dedupe/bb__dup1.jpg=same", "2019/a.jpg=same"),
            tree(),
        )
    }

    @Test
    fun `Dedupe on a case-sensitive folder uses the existing Trash folder of another case`() {
        val sensitive = FakeFileSystemRepository()
        file("trash/2019/Dedupe/c.jpg", "old", sensitive)
        file("2019/a.jpg", "same", sensitive)
        file("2019/bb.jpg", "same", sensitive)
        val planner = DedupePlanner(sensitive)
        val location = TrashLocation(sensitive.root, "2019")
        val year = sensitive.list(sensitive.root).single { it.name == "2019" }.handle

        planner.apply(planner.plan(year, location), location, control)

        assertEquals(
            setOf("trash/2019/Dedupe/c.jpg=old", "trash/2019/Dedupe/bb.jpg=same", "2019/a.jpg=same"),
            tree(sensitive.root, on = sensitive),
        )
    }

    @Test
    fun `the exact Trash folder wins over one of another case`() {
        val sensitive = FakeFileSystemRepository()
        file("TRASH/2019/Dedupe/upper.jpg", "one", sensitive)
        file("Trash/2019/Dedupe/exact.jpg", "two", sensitive)

        val listed = TrashScanner(sensitive).scan(sensitive.root).entries.map { it.name }

        assertEquals(listOf("exact.jpg"), listed)
    }

    @Test
    fun `Visual Dupes numbers a file whose name is in the Trash in another case`() {
        file("Trash/2019/VisualDupes/B.JPG", "old")
        val keep = file("2019/a.jpg", "keep")
        val dupe = file("2019/b.jpg", "dupe")
        val group = listOf(VisualDupeMember(keep, "a.jpg", 4), VisualDupeMember(dupe, "b.jpg", 4))

        val result = moveUncheckedToTrash(
            fs,
            TrashLocation(fs.root, "2019"),
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

    private fun reorg(keepInput: Boolean = true, keepLibrary: Boolean = true) {
        val output = folder("Library")
        val planner = ReorgPlanner(fs, reader)
        val plan = planner.plan(output, listOf(ReorgSource(folder("Camera"), "Camera", InputMode.MOVE)))
        val choices = plan.duplicates.map { DuplicateChoice(it, keepInput, keepLibrary) }
        val result = planner.apply(plan, output, choices, control)
        assertEquals(emptyList<Any>(), result.failures)
    }

    @Test
    fun `Reorg numbers a file whose name is in the year folder in another case`() {
        shot("Library/2019/X.JPG", "library")
        shot("Camera/x.jpg", "input")

        reorg()

        assertEquals(setOf("Library/2019/X.JPG=library", "Library/2019/x__year1.jpg=input"), tree())
    }

    @Test
    fun `Reorg numbers a duplicate whose name is in its Trash in another case`() {
        file("Library/Trash/2019/Reorg/X.JPG", "old")
        shot("Library/2019/y.jpg", "same")
        shot("Camera/x.jpg", "same")

        reorg(keepInput = false)

        assertEquals(
            setOf(
                "Library/Trash/2019/Reorg/X.JPG=old",
                "Library/Trash/2019/Reorg/x__dup1.jpg=same",
                "Library/2019/y.jpg=same",
            ),
            tree(),
        )
    }

    private fun trashEntries() = TrashScanner(fs).scan(fs.root).entries

    @Test
    fun `a restore onto a name in another case is a conflict, and Keep both numbers it`() {
        file("2019/A.JPG", "library")
        file("Trash/2019/Dedupe/a.jpg", "trashed")
        val restorer = TrashRestorer(fs)

        val plan = restorer.plan(fs.root, trashEntries())
        assertEquals(emptyList<Any>(), plan.direct)
        assertNull(restorer.resolve(fs.root, plan.conflicts.single(), ConflictChoice.BOTH))

        assertEquals(setOf("2019/A.JPG=library", "2019/a__dup1.jpg=trashed"), tree())
    }

    @Test
    fun `of two library files that differ only by case the restore asks about the exact one`() {
        val sensitive = FakeFileSystemRepository()
        file("2019/A.JPG", "upper", sensitive)
        file("2019/a.jpg", "lower", sensitive)
        file("Trash/2019/Dedupe/a.jpg", "trashed", sensitive)
        val entries = TrashScanner(sensitive).scan(sensitive.root).entries

        val plan = TrashRestorer(sensitive).plan(sensitive.root, entries)

        assertEquals("a.jpg", plan.conflicts.single().existing.name)
    }

    @Test
    fun `replacing a library file of another case parks it under its own name`() {
        file("2019/A.JPG", "library")
        file("Trash/2019/Dedupe/a.jpg", "trashed")
        file("Trash/2019/Replaced/a.jpg", "older")
        val restorer = TrashRestorer(fs)
        val conflict = restorer.plan(fs.root, trashEntries().filter { it.action == TrashAction.DEDUPE })
            .conflicts.single()

        assertNull(restorer.resolve(fs.root, conflict, ConflictChoice.TRASH_COPY))

        assertEquals(
            setOf("2019/a.jpg=trashed", "Trash/2019/Replaced/a.jpg=older", "Trash/2019/Replaced/A__dup1.JPG=library"),
            tree(),
        )
    }

    @Test
    fun `a replace that fails gives the library file its own name back`() {
        file("2019/A.JPG", "library")
        val trashed = file("Trash/2019/Dedupe/a.jpg", "trashed")
        val faulty = FaultyFileSystemRepository(fs)
        faulty.failMove = { if (it == trashed) FileAlreadyExistsException("appeared") else null }
        val restorer = TrashRestorer(faulty)
        val conflict = restorer.plan(fs.root, trashEntries()).conflicts.single()

        restorer.resolve(fs.root, conflict, ConflictChoice.TRASH_COPY)

        assertEquals(setOf("2019/A.JPG=library", "Trash/2019/Dedupe/a.jpg=trashed"), tree())
    }

    @Test
    fun `two Trash files that differ only by case are restored under two names`() {
        file("Trash/2019/Dedupe/a.jpg", "one")
        file("Trash/2019/VisualDupes/A.JPG", "two")
        val restorer = TrashRestorer(fs)

        val result = restorer.restore(fs.root, restorer.plan(fs.root, trashEntries()), control)

        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(setOf("2019/a.jpg=one", "2019/A_VisualDupes.JPG=two"), tree())
    }

    @Test
    fun `restoring from a lower case trash folder prunes the folders it empties`() {
        file("trash/2019/dedupe/a.jpg", "one")
        val restorer = TrashRestorer(fs)

        val result = restorer.restore(fs.root, restorer.plan(fs.root, trashEntries()), control)

        assertEquals(1, result.restored)
        assertEquals(2, result.prunedFolders)
        assertEquals(setOf("2019/a.jpg=one"), tree())
    }
}
