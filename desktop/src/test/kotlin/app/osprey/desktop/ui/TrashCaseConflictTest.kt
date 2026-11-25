package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.history.openHistory
import app.osprey.jobs.JobState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 10_000L

/**
 * A restore, clicked through the whole app, onto a library file whose name differs only by case (#132): it asks, shows
 * each side under its own name, and "Keep both" keeps both files.
 */
@OptIn(ExperimentalTestApi::class)
class TrashCaseConflictTest : TimedTest() {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val year get() = library.resolve("2021")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-trash-case")
        Files.writeString(Files.createDirectories(year).resolve("A.JPG"), "library")
        Files.writeString(Files.createDirectories(library.resolve("Trash/2021/Dedupe")).resolve("a.jpg"), "trashed")
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun launch() = AppState(
        DesktopFileSystemRepository(),
        DesktopMediaMetadataReader(),
        FakeSettingsStore(),
        history = openHistory(root.resolve("history").toFile()),
    ).also { it.openLibrary(library) }

    private fun ComposeUiTest.waitForJobs(state: AppState) {
        waitForIdle()
        runBlocking {
            withTimeout(WAIT_MS) {
                state.jobs.entries.first { entries -> entries.all { it.state is JobState.Finished } }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.click(state: AppState, text: String) {
        onNodeWithText(text).performClick()
        waitForJobs(state)
    }

    @Test
    fun `restoring onto a name of another case asks with both names and Keep both keeps both`() = runComposeUiTest {
        val state = launch()
        setContent { OspreyApp(state) }

        click(state, "🗑")
        click(state, "Select all in this tab")
        click(state, "Restore")

        onNodeWithText("a.jpg already exists in 2021").assertExists()
        onNodeWithText("A.JPG", substring = true).assertExists()
        click(state, "Keep both")

        val restored = year.toFile().listFiles()!!.associate { it.name to it.readText() }
        assertEquals(mapOf("A.JPG" to "library", "a__dup1.jpg" to "trashed"), restored)
    }
}
