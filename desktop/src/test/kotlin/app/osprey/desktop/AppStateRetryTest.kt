package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val KEPT = "a.jpg"
private const val EXTRA = "a (1).jpg"

/** Retrying the failed files of a run from the app state (#83), on real files with one move made to fail. */
class AppStateRetryTest : TimedTest() {

    private lateinit var library: Path
    private val history = InMemoryJobHistoryStore()
    private val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
    private lateinit var state: AppState

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-retry-test")
        Files.createDirectory(library.resolve("2021"))
        Files.writeString(library.resolve("2021").resolve(KEPT), "same bytes")
        Files.writeString(library.resolve("2021").resolve(EXTRA), "same bytes")
        fs.failMove = { if ((it as PathHandle).path.fileName.toString() == EXTRA) IOException("locked") else null }
        state = AppState(fs, DesktopMediaMetadataReader(), FakeSettingsStore(), history, immediateJobQueue(history))
        state.openLibrary(library)
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun failedDedupe(): RunRecord {
        state.dedupe.onScan(checkNotNull(state.yearHandle()), state.selectedYear)
        state.dedupe.onApply(checkNotNull(state.yearHandle()), state.selectedYear)
        return history.runs().first { it.action == "Dedupe 2021 apply" }
    }

    private fun trashed() = library.resolve("Trash/2021/Dedupe").resolve(EXTRA)

    @Test
    fun `a dedupe apply is recorded with its kind, library and year, and fails on the locked file`() {
        val run = failedDedupe()

        assertEquals(RunOrigin("dedupe", library.toString(), "2021"), run.origin)
        assertEquals(RunState.Failed, run.state)
        assertEquals("2021/$EXTRA", run.files.single { it.result == FileResult.Failed }.path)
    }

    @Test
    fun `a failed run can be retried on its own library and year`() {
        assertTrue(state.canRetry(failedDedupe()))
    }

    @Test
    fun `the retry is hidden once another year or another library is selected`() {
        val run = failedDedupe()

        state.selectedYear = "2020"
        assertFalse(state.canRetry(run))
        state.selectedYear = "2021"
        assertTrue(state.canRetry(run))
        state.libraryPath = library.resolve("elsewhere")
        assertFalse(state.canRetry(run))
    }

    @Test
    fun `retrying a dedupe run moves the file that failed and records a run linked to the first`() {
        val run = failedDedupe()
        fs.failMove = { null }

        state.retry(run)

        assertTrue(Files.exists(trashed()))
        assertFalse(Files.exists(library.resolve("2021").resolve(EXTRA)))
        val retry = history.runs().first { it.action == "Dedupe 2021 retry" }
        assertEquals(RunOrigin("dedupe", library.toString(), "2021", retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
    }

    @Test
    fun `a run that cannot be retried starts nothing`() {
        val done = failedDedupe().copy(state = RunState.Done)

        state.retry(done)

        assertTrue(history.runs().none { it.action == "Dedupe 2021 retry" })
        assertFalse(Files.exists(trashed()))
    }

    private fun failedRunOf(id: Long, kind: String, scope: String?) = RunRecord(
        id = id,
        action = "$kind apply",
        state = RunState.Failed,
        queuedAt = Instant.EPOCH,
        files = listOf(FileRecord("2021/gone.jpg", FileResult.Failed, "locked")),
        origin = RunOrigin(kind, library.toString(), scope),
    )

    @Test
    fun `retrying a run of every kind starts the retry of that kind, linked to the run`() {
        val kinds = listOf(
            "flatten" to "2021", "rename" to "2021", "dedupe" to "2021", "dates" to "2021",
            "standardize" to "2021", "visual-dupes" to "2021", "reorg" to null,
            "trash-delete" to null, "trash-restore" to null,
        )
        kinds.forEachIndexed { index, (kind, scope) ->
            val run = failedRunOf(900L + index, kind, scope)
            assertTrue(kind, state.canRetry(run))

            state.retry(run)

            val retry = history.runs().singleOrNull { it.origin?.retryOf == run.id }
            assertNotNull("no retry run for $kind", retry)
            assertEquals(kind, kind, retry?.origin?.kind)
            assertEquals(
                kind,
                listOf(FileRecord("2021/gone.jpg", FileResult.Skipped, "no longer there or already handled")),
                retry?.files,
            )
        }
    }
}
