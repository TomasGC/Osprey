package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunState
import app.osprey.jobs.JobOutcome
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppStateTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-appstate-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun newState() = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore())

    @Test
    fun `openLibrary lists only numeric-named subfolders, sorted, as years`() {
        Files.createDirectory(tempDir.resolve("2023"))
        Files.createDirectory(tempDir.resolve("2021"))
        Files.createDirectory(tempDir.resolve("2022"))
        Files.createDirectory(tempDir.resolve("TODELETE"))
        Files.writeString(tempDir.resolve("notes.txt"), "x")

        val state = newState()
        state.openLibrary(tempDir)

        assertEquals(listOf("2021", "2022", "2023"), state.years)
    }

    @Test
    fun `openLibrary selects the most recent year by default`() {
        Files.createDirectory(tempDir.resolve("2021"))
        Files.createDirectory(tempDir.resolve("2023"))

        val state = newState()
        state.openLibrary(tempDir)

        assertEquals("2023", state.selectedYear)
    }

    @Test
    fun `yearHandle is null until both a library and a year are selected`() {
        assertNull(newState().yearHandle())
    }

    private fun stateWith(history: InMemoryJobHistoryStore) =
        AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore(), history = history)
            .also { it.openLibrary(tempDir) }

    @Test
    fun `the History screen opens on its list and closes on the second tap`() {
        val history = InMemoryJobHistoryStore()
        val id = history.begin("Reorg", Instant.EPOCH)
        val state = stateWith(history)

        state.toggleHistory()
        state.historyScreen.open(id)
        assertTrue(state.showsHistory)
        state.toggleHistory()
        assertFalse(state.showsHistory)
        state.toggleHistory()

        assertTrue(state.showsHistory)
        assertNull(state.historyScreen.view.value.selected)
    }

    @Test
    fun `the History screen does not open before a library is chosen`() {
        val state = newState()

        state.toggleHistory()

        assertFalse(state.showsHistory)
    }

    @Test
    fun `the History screen shows the runs of the history it was given`() {
        val history = InMemoryJobHistoryStore().also { it.begin("Reorg", Instant.EPOCH) }
        val state = stateWith(history)

        state.toggleHistory()

        assertEquals("Reorg", state.historyScreen.view.value.sections.single().runs.single().action)
    }

    @Test
    fun `opening the History closes the Trash and Settings, and opening the Trash closes the History`() {
        val state = stateWith(InMemoryJobHistoryStore())
        state.toggleTrash()
        state.settingsState.showing = true

        state.toggleHistory()
        assertTrue(state.showsHistory)
        assertFalse(state.showsTrash)
        assertFalse(state.settingsState.showing)
        state.toggleTrash()

        assertTrue(state.showsTrash)
        assertFalse(state.showsHistory)
    }

    @Test
    fun `a job queued through the app state is recorded in its history`() {
        val history = InMemoryJobHistoryStore()
        val state = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore(), history = history)

        runBlocking {
            withTimeout(WAIT_MS) { state.jobs.enqueue("Probe") { JobOutcome("Probe: 1 done") }.await() }
        }

        val run = history.runs().single()
        assertEquals("Probe", run.action)
        assertEquals(RunState.Done, run.state)
        assertEquals("Probe: 1 done", run.summary)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
