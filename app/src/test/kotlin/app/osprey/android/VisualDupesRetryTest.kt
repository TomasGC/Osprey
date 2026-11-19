package app.osprey.android

import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.PixelBuffer
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LIBRARY = "content://library"
private const val PICTURE_SIDE = 8

/** The retry of the failed files of a Visual Dupes group move (#83), with one of the files locked. */
class VisualDupesRetryTest {

    private val history = InMemoryJobHistoryStore()
    private val fake = FakeFileSystemRepository()
    private val fs = FaultyFileSystemRepository(fake)
    private val reader = FakeMediaMetadataReader()
    private val year = fake.addFolder(fake.root, "2019")
    private var label = LIBRARY
    private lateinit var locked: FileHandle
    private lateinit var controller: VisualDupesController

    @Before
    fun setUp() {
        picture("a.jpg")
        locked = picture("bb.jpg")
        fs.failMove = { if (it == locked) IOException("locked") else null }
        controller = VisualDupesController(fs, reader, immediateJobQueue(history), { label }) {
            TrashLocation(fake.root, "2019")
        }
    }

    private fun picture(name: String): FileHandle {
        val handle = fake.addFile(year, name, name.toByteArray())
        reader.setFrame(
            handle,
            PixelBuffer(PICTURE_SIDE, PICTURE_SIDE, IntArray(PICTURE_SIDE * PICTURE_SIDE) { it * 3 }),
        )
        return handle
    }

    private fun failedGroupMove(): RunRecord {
        controller.onScan(year, "2019")
        val group = controller.groupsIn(year).single()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false
        controller.moveUnchecked(year, group)
        return history.runs().first { it.action.startsWith("Visual Dupes 2019 group") }
    }

    private fun retries() = history.runs().filter { it.action == "Visual Dupes 2019 retry" }

    private fun names(folder: FileHandle) = fake.list(folder).map { it.name }.toSet()

    private fun trashedNames(): Set<String> {
        val trash = fake.list(fake.root).firstOrNull { it.name == "Trash" } ?: return emptySet()
        val folder = fake.list(trash.handle).first { it.name == "2019" }
        val action = fake.list(folder.handle).firstOrNull { it.name == "VisualDupes" } ?: return emptySet()
        return names(action.handle)
    }

    @Test
    fun `a group move is recorded with its kind, library and year, and fails on the locked file`() {
        val run = failedGroupMove()

        assertEquals(RunOrigin("visual-dupes", LIBRARY, "2019"), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals(listOf("2019/bb.jpg"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
    }

    @Test
    fun `a retry moves the file that failed and records a run linked to the first`() {
        val run = failedGroupMove()
        fs.failMove = { null }

        controller.onRetry(year, run, "2019")

        assertEquals(setOf("bb.jpg"), trashedNames())
        assertEquals(setOf("a.jpg"), names(year))
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

        controller.onRetry(year, run, "2019")

        val retried = retries().single()
        assertEquals(RunState.Failed, retried.state)
        assertTrue(retried.summary, retried.summary.endsWith("moved 0, 1 failed"))
        assertTrue("bb.jpg" in names(year))
    }

    @Test
    fun `a failed file that is gone is reported skipped, not fatal`() {
        val run = failedGroupMove()
        fake.delete(locked)

        controller.onRetry(year, run, "2019")

        assertEquals(
            listOf(FileRecord("2019/bb.jpg", FileResult.Skipped, "no longer there or already handled")),
            retries().single().files,
        )
    }

    @Test
    fun `a run that cannot be retried, of another library, year or kind, starts nothing`() {
        val run = failedGroupMove()

        controller.onRetry(year, run.copy(state = RunState.Done), "2019")
        label = "content://elsewhere"
        controller.onRetry(year, run, "2019")
        label = LIBRARY
        controller.onRetry(year, run, "2020")
        controller.onRetry(year, run.copy(origin = RunOrigin("dedupe", LIBRARY, "2019")), "2019")

        assertTrue(retries().isEmpty())
    }
}
