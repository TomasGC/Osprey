package app.osprey.history

import app.osprey.actions.dates.DATES_RETRY
import app.osprey.actions.dates.DatesFix
import app.osprey.actions.dates.DatesPlan
import app.osprey.actions.dedupe.DEDUPE_RETRY
import app.osprey.actions.dedupe.DedupeMove
import app.osprey.actions.dedupe.DedupePlan
import app.osprey.actions.flatten.FLATTEN_RETRY
import app.osprey.actions.flatten.FlattenFolder
import app.osprey.actions.flatten.FlattenMove
import app.osprey.actions.flatten.FlattenPlan
import app.osprey.actions.rename.RENAME_RETRY
import app.osprey.actions.rename.RenameAction
import app.osprey.actions.rename.RenamePlan
import app.osprey.actions.standardize.STANDARDIZE_RETRY
import app.osprey.actions.standardize.StandardizePlan
import app.osprey.actions.standardize.StandardizeRename
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

private val NOON: LocalDateTime = LocalDateTime.of(2021, 6, 15, 12, 0)

private fun handle(): FileHandle = object : FileHandle {}

private val A = handle()
private val B = handle()
private val C = handle()

class RetryFiltersTest {

    private val dedupe = DedupePlan(
        listOf(DedupeMove(A, "a.jpg", "a.jpg", "k"), DedupeMove(B, "b.jpg", "b.jpg", "k"), DedupeMove(C, "c.jpg", "c.jpg", "k")),
    )

    @Test
    fun `a dedupe plan names its moves by the file they move`() {
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), DEDUPE_RETRY.names(dedupe))
    }

    @Test
    fun `a dedupe retry keeps the moves of the named files in plan order and ignores unknown names`() {
        assertEquals(
            DedupePlan(listOf(dedupe.moves[0], dedupe.moves[2])),
            DEDUPE_RETRY.only(dedupe, setOf("c.jpg", "a.jpg", "gone.jpg")),
        )
        assertEquals(DedupePlan(emptyList()), DEDUPE_RETRY.only(dedupe, emptySet()))
    }

    private val rename = RenamePlan(
        renames = listOf(RenameAction(A, "a (1).jpg", "a.jpg"), RenameAction(B, "b (1).jpg", "b.jpg")),
        skips = listOf(RenameAction(C, "c (1).jpg", "c.jpg")),
    )

    @Test
    fun `a rename plan names its renames by the file they rename`() {
        assertEquals(listOf("a (1).jpg", "b (1).jpg"), RENAME_RETRY.names(rename))
    }

    @Test
    fun `a rename retry keeps the named renames and drops the skips`() {
        assertEquals(
            RenamePlan(listOf(rename.renames[1]), emptyList()),
            RENAME_RETRY.only(rename, setOf("b (1).jpg", "c (1).jpg")),
        )
    }

    private val dates = DatesPlan(
        fixes = listOf(DatesFix(A, "a.jpg", NOON, MediaDateSource.EXIF), DatesFix(B, "b.jpg", NOON, MediaDateSource.FILENAME)),
        unfixable = listOf(FileEntry(C, "c.jpg", isDirectory = false, sizeBytes = 3)),
    )

    @Test
    fun `a dates plan names its fixes by the file they fix`() {
        assertEquals(listOf("a.jpg", "b.jpg"), DATES_RETRY.names(dates))
    }

    @Test
    fun `a dates retry keeps the named fixes and drops the unfixable files`() {
        assertEquals(DatesPlan(listOf(dates.fixes[0]), emptyList()), DATES_RETRY.only(dates, setOf("a.jpg", "c.jpg")))
    }

    private val standardize = StandardizePlan(
        renames = listOf(
            StandardizeRename(A, "a.jpg", "IMG_20210615_120000.jpg", NOON, MediaDateSource.EXIF),
            StandardizeRename(B, "b.jpg", "IMG_20210615_120001.jpg", NOON, MediaDateSource.EXIF),
        ),
        unfixable = listOf(FileEntry(C, "c.jpg", isDirectory = false, sizeBytes = 3)),
    )

    @Test
    fun `a standardize plan names its renames by the file they rename`() {
        assertEquals(listOf("a.jpg", "b.jpg"), STANDARDIZE_RETRY.names(standardize))
    }

    @Test
    fun `a standardize retry keeps the named renames and drops the unfixable files`() {
        assertEquals(
            StandardizePlan(listOf(standardize.renames[1]), emptyList()),
            STANDARDIZE_RETRY.only(standardize, setOf("b.jpg")),
        )
    }

    private val flatten = FlattenPlan(
        moves = listOf(FlattenMove(A, "06/15/a.jpg", "a.jpg", ""), FlattenMove(B, "06/b.jpg", "b.jpg", "")),
        folders = listOf(FlattenFolder(C, "06/15"), FlattenFolder(C, "06")),
    )

    @Test
    fun `a flatten plan names its moves by the path they move from`() {
        assertEquals(listOf("06/15/a.jpg", "06/b.jpg"), FLATTEN_RETRY.names(flatten))
    }

    @Test
    fun `a flatten retry keeps the named moves and every folder`() {
        assertEquals(
            FlattenPlan(listOf(flatten.moves[1]), flatten.folders),
            FLATTEN_RETRY.only(flatten, setOf("06/b.jpg")),
        )
    }

    private fun failedRun(scope: String?, vararg files: FileRecord) = RunRecord(
        1, "Dedupe apply", RunState.Failed, queuedAt = java.time.Instant.EPOCH, files = files.toList(),
        origin = RunOrigin("dedupe", "D:/Photos", scope),
    )

    private fun failed(path: String) = FileRecord(path, FileResult.Failed, "locked")

    @Test
    fun `the failed names are the failed paths without the scope folder`() {
        val run = failedRun("2021", failed("2021/a.jpg"), failed("2021/sub/b.jpg"), FileRecord("2021/c.jpg", FileResult.Done))

        assertEquals(setOf("a.jpg", "sub/b.jpg"), RetryPlanner.failedNames(run))
    }

    @Test
    fun `a run with no scope keeps its failed paths as they are`() {
        assertEquals(setOf("2021/a.jpg"), RetryPlanner.failedNames(failedRun(null, failed("2021/a.jpg"))))
    }

    @Test
    fun `a run that does not know where it came from keeps its failed paths as they are`() {
        val run = failedRun("2021", failed("2021/a.jpg")).copy(origin = null)

        assertEquals(setOf("2021/a.jpg"), RetryPlanner.failedNames(run))
    }

    @Test
    fun `a failed path that is not under the scope is kept as it is`() {
        assertEquals(setOf("other/c.jpg"), RetryPlanner.failedNames(failedRun("2021", failed("other/c.jpg"))))
    }
}
