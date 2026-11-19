package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LIBRARY = "D:/Photos"
private const val PICTURE_SIDE = 8

/** The retry of the failed files of a Visual Dupes group move (#83), on real files with one of them locked. */
class VisualDupesRetryTest : TimedTest() {

    private object SamePictureReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer =
            PixelBuffer(PICTURE_SIDE, PICTURE_SIDE, IntArray(PICTURE_SIDE * PICTURE_SIDE) { it * 3 })
    }

    private lateinit var library: Path
    private lateinit var year: Path
    private val history = InMemoryJobHistoryStore()
    private val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
    private var label = LIBRARY
    private lateinit var controller: VisualDupesController

    private val yearDir get() = PathHandle(year)

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-visual-retry-test")
        year = Files.createDirectory(library.resolve("2019"))
        Files.writeString(year.resolve("a.jpg"), "aaaa")
        Files.writeString(year.resolve("b.jpg"), "bb")
        fs.failMove = { if ((it as PathHandle).path.fileName.toString() == "b.jpg") IOException("locked") else null }
        controller = VisualDupesController(fs, SamePictureReader, immediateJobQueue(history), { label }) {
            TrashLocation(PathHandle(library), "2019")
        }
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun failedGroupMove(): RunRecord {
        controller.onScan(yearDir, "2019")
        val group = controller.groupsIn(yearDir).single()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        controller.moveUnchecked(yearDir, group)
        return history.runs().first { it.action.startsWith("Visual Dupes 2019 group") }
    }

    private fun retries() = history.runs().filter { it.action == "Visual Dupes 2019 retry" }

    private fun trashed() = library.resolve("Trash/2019/VisualDupes").resolve("b.jpg")

    @Test
    fun `a group move is recorded with its kind, library and year, and fails on the locked file`() {
        val run = failedGroupMove()

        assertEquals(RunOrigin("visual-dupes", LIBRARY, "2019"), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals(listOf("2019/b.jpg"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
    }

    @Test
    fun `a retry moves the file that failed and records a run linked to the first`() {
        val run = failedGroupMove()
        fs.failMove = { null }

        controller.onRetry(yearDir, run, "2019")

        assertTrue(Files.exists(trashed()))
        assertTrue(Files.exists(year.resolve("a.jpg")))
        val retried = retries().single()
        assertEquals(RunOrigin("visual-dupes", LIBRARY, "2019", retryOf = run.id), retried.origin)
        assertEquals(RunState.Done, retried.state)
        assertTrue(
            retried.summary,
            retried.summary.startsWith("Retry of run #${run.id}: Visual Dupes 2019 retry: moved 1"),
        )
    }

    @Test
    fun `a file that still fails is failed again`() {
        val run = failedGroupMove()

        controller.onRetry(yearDir, run, "2019")

        val retried = retries().single()
        assertEquals(RunState.Failed, retried.state)
        assertTrue(retried.summary, retried.summary.endsWith("moved 0, 1 failed"))
        assertTrue(Files.exists(year.resolve("b.jpg")))
    }

    @Test
    fun `a failed file that is gone is reported skipped, not fatal`() {
        val run = failedGroupMove()
        Files.delete(year.resolve("b.jpg"))

        controller.onRetry(yearDir, run, "2019")

        assertEquals(
            listOf(FileRecord("2019/b.jpg", FileResult.Skipped, "no longer there or already handled")),
            retries().single().files,
        )
    }

    @Test
    fun `a run that cannot be retried, of another library, year or kind, starts nothing`() {
        val run = failedGroupMove()

        controller.onRetry(yearDir, run.copy(state = RunState.Done), "2019")
        label = "E:/Elsewhere"
        controller.onRetry(yearDir, run, "2019")
        label = LIBRARY
        controller.onRetry(yearDir, run, "2020")
        controller.onRetry(yearDir, run.copy(origin = RunOrigin("dedupe", LIBRARY, "2019")), "2019")

        assertTrue(retries().isEmpty())
    }
}
