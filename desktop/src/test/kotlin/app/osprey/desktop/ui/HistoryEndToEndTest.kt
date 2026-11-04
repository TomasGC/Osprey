package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.history.openHistory
import app.osprey.jobs.JobState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 10_000L

/**
 * The History (#79) through the whole desktop app: the real shell, a real [AppState] over real temp folders and a real
 * history on disk. What a person does with the mouse is done here with the test's clicks, and a new launch is a new
 * [AppState] over the same folder. Jobs are awaited on the queue's own state, never by polling the screen.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryEndToEndTest {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val year get() = library.resolve("2021")
    private val historyDir get() = root.resolve("history").toFile()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-history-e2e")
        Files.createDirectories(year.resolve("sub"))
        listOf("a", "b", "c").forEach { Files.writeString(year.resolve("sub").resolve("$it.jpg"), "photo $it") }
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    /** A launch of the app: the history is opened from its folder, which closes any run an earlier launch left open. */
    private fun launch() =
        AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore(), history = openHistory(historyDir))
            .also { it.openLibrary(library) }

    private fun waitForJobs(state: AppState) = runBlocking {
        withTimeout(WAIT_MS) {
            state.jobs.entries.first { entries -> entries.isNotEmpty() && entries.all { it.state is JobState.Finished } }
        }
    }

    private fun ComposeUiTest.click(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.flattenThroughTheScreen(state: AppState) {
        click("Flatten")
        click("Scan")
        waitForJobs(state)
        waitForIdle()
        click("Apply")
        waitForJobs(state)
        waitForIdle()
    }

    @Test
    fun `a Flatten run on a year goes through the whole app and into the History`() = runComposeUiTest {
        val state = launch()
        setContent { OspreyApp(state) }

        flattenThroughTheScreen(state)

        onNodeWithText("Flatten 2021 apply: 3 done").assertExists()
        onNodeWithText("Flatten 2021 scan: count=3").assertExists()
        assertTrue(Files.exists(year.resolve("a.jpg")))
        assertFalse(Files.exists(year.resolve("sub").resolve("a.jpg")))
        click("🕘")
        onNodeWithText("Done (2)").assertExists()
        onNode(hasText("Flatten 2021 apply") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("sub/a.jpg: moved up as a.jpg").assertExists()
        click("Back")
        onNodeWithText("Done (2)").assertExists()
    }

    @Test
    fun `Clear history asks first, keeps the runs on Cancel and removes them and their files on Forget`() =
        runComposeUiTest {
            val state = launch()
            setContent { OspreyApp(state) }
            flattenThroughTheScreen(state)
            click("🕘")

            click("Clear history")
            onNodeWithText("Forget 2 ended runs? A run that is waiting or running stays.").assertExists()
            click("Cancel")
            assertEquals(2, historyDir.list()!!.size)
            click("Clear history")
            click("Forget")

            onNodeWithText("No runs yet.").assertExists()
            assertEquals(0, historyDir.list()!!.size)
        }

    @Test
    fun `a new launch shows the runs of the last one and closes a run the app died in`() = runComposeUiTest {
        val first = launch()
        val yearHandle = PathHandle(year)
        first.flatten.onScan(yearHandle, "2021")
        waitForJobs(first)
        first.flatten.onApply(yearHandle, "2021")
        waitForJobs(first)
        // What a killed app leaves behind: a run that began and never ended.
        Files.writeString(
            historyDir.toPath().resolve("99.jsonl"),
            """{"t":"run","id":99,"action":"Flatten 2021 scan","at":1793737437979}""" + "\n" +
                """{"t":"start","at":1793737437986}""" + "\n",
        )

        val second = launch()
        setContent { OspreyApp(second) }
        click("🕘")

        onNodeWithText("Interrupted (1)").assertExists()
        onNodeWithText("Done (2)").assertExists()
    }
}
