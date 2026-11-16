package app.osprey.android

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val KEPT = "a.jpg"
private const val EXTRA = "a (1).jpg"
private const val LIBRARY = "content://library"
private val BYTES = "same bytes".toByteArray()

/** Retrying the failed files of a run from the app state (#83), with one move made to fail. */
class AppStateRetryTest {

    private class NoStore : SettingsStore {
        override fun load(): MediaSettings = MediaSettings()

        override fun save(settings: MediaSettings) = Unit

        override fun hasSavedSettings(): Boolean = false
    }

    private val history = InMemoryJobHistoryStore()
    private val fake = FakeFileSystemRepository()
    private val fs = FaultyFileSystemRepository(fake)
    private val state = AndroidAppState(
        fs, FakeMediaMetadataReader(), NoStore(), immediateJobQueue(history), { null }, {}, history,
    )

    @Before
    fun setUp() {
        val year = fake.addFolder(fake.root, "2021")
        fake.addFile(year, KEPT, BYTES)
        val extra = fake.addFile(year, EXTRA, BYTES)
        fs.failMove = { if (it == extra) IOException("locked") else null }
        state.libraryReference = LIBRARY
        state.openLibrary(fake.root)
    }

    private fun failedDedupe(): RunRecord {
        state.dedupe.onScan(checkNotNull(state.yearHandle()), state.selectedYear)
        state.dedupe.onApply(checkNotNull(state.yearHandle()), state.selectedYear)
        return history.runs().first { it.action == "Dedupe 2021 apply" }
    }

    private fun trashedNames(): Set<String> {
        val trash = fs.list(fake.root).find { it.name == "Trash" } ?: return emptySet()
        val year = fs.list(trash.handle).find { it.name == "2021" } ?: return emptySet()
        val dedupe = fs.list(year.handle).find { it.name == "Dedupe" } ?: return emptySet()
        return fs.list(dedupe.handle).map { it.name }.toSet()
    }

    @Test
    fun `a dedupe apply is recorded with its kind, library and year, and fails on the locked file`() {
        val run = failedDedupe()

        assertEquals(RunOrigin("dedupe", LIBRARY, "2021"), run.origin)
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
        state.libraryReference = "content://elsewhere"
        assertFalse(state.canRetry(run))
    }

    @Test
    fun `retrying a dedupe run moves the file that failed and records a run linked to the first`() {
        val run = failedDedupe()
        fs.failMove = { null }

        state.retry(run)

        assertEquals(setOf(EXTRA), trashedNames())
        val retry = history.runs().first { it.action == "Dedupe 2021 retry" }
        assertEquals(RunOrigin("dedupe", LIBRARY, "2021", retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
    }

    @Test
    fun `a run that cannot be retried starts nothing`() {
        val done = failedDedupe().copy(state = RunState.Done)

        state.retry(done)

        assertTrue(history.runs().none { it.action == "Dedupe 2021 retry" })
        assertEquals(emptySet<String>(), trashedNames())
    }

    private fun failedRunOf(id: Long, kind: String, scope: String?) = RunRecord(
        id = id,
        action = "$kind apply",
        state = RunState.Failed,
        queuedAt = Instant.EPOCH,
        files = listOf(FileRecord("2021/gone.jpg", FileResult.Failed, "locked")),
        origin = RunOrigin(kind, LIBRARY, scope),
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
