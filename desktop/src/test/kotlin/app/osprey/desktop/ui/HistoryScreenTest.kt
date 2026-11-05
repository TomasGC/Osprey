package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.testing.TimedTest
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import app.osprey.history.HistoryController
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunState
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val T0: Instant = Instant.parse("2019-05-04T12:00:00Z")

@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest : TimedTest() {

    private val store = InMemoryJobHistoryStore()
    private val controller = HistoryController(store)

    private fun ended(action: String, state: RunState, summary: String, vararg files: FileRecord) {
        val id = store.begin(action, T0)
        store.start(id, T0)
        files.forEach { store.appendFile(id, it) }
        store.appendLog(id, "$action log line")
        store.finish(id, state, summary, T0.plusSeconds(4))
    }

    private fun threeRuns() {
        ended(
            "Dates", RunState.Failed, "Dates apply: 1 done, 1 failed",
            FileRecord(
                "/a.jpg", FileResult.Done,
                before = FileState(1024, modified = T0, name = "a.jpg"),
                after = FileState(1024, modified = T0.plusSeconds(60), name = "a.jpg"),
            ),
            FileRecord("/b.jpg", FileResult.Failed, "boom"),
        )
        ended("Reorg", RunState.Done, "Reorg apply: 1 done", FileRecord("/c.jpg", FileResult.Done, "moved"))
        ended("Rename", RunState.Done, "Rename apply: 1 done", FileRecord("/d.jpg", FileResult.Done))
    }

    private fun ComposeUiTest.screen() {
        controller.refresh()
        setContent { HistoryScreen(controller, ZoneOffset.UTC) }
        waitForIdle()
    }

    @Test
    fun `the runs are listed by state with their counts and a line each`() = runComposeUiTest {
        threeRuns()

        screen()

        onNodeWithText("Failed (1)").assertExists()
        onNodeWithText("Done (2)").assertExists()
        onNodeWithText("Dates").assertExists()
        onNodeWithText("Dates apply: 1 done, 1 failed").assertExists()
        onNodeWithText("2 files: 1 done, 1 failed").assertExists()
        onAllNodesWithText("2019-05-04 12:00:00").assertCountEquals(3)
    }

    @Test
    fun `an empty history says so and has nothing to clear`() = runComposeUiTest {
        screen()

        onNodeWithText("No runs yet.").assertExists()
        onNodeWithText("Clear history").assertIsNotEnabled()
    }

    @Test
    fun `a waiting run alone leaves Clear history disabled`() = runComposeUiTest {
        store.begin("Dates", T0)

        screen()

        onNodeWithText("Waiting (1)").assertExists()
        onNodeWithText("Clear history").assertIsNotEnabled()
    }

    @Test
    fun `a run opens on its page with its summary, log, files and before and after`() = runComposeUiTest {
        threeRuns()
        screen()

        onNodeWithText("Dates").performClick()
        waitForIdle()

        onNodeWithText("Back").assertExists()
        onNodeWithText("Failed").assertExists()
        onNodeWithText("Dates apply: 1 done, 1 failed").assertExists()
        onNodeWithText("2019-05-04 12:00:00 · took 4 s").assertExists()
        onNodeWithText("2 files: 1 done, 1 failed").assertExists()
        onNodeWithText("Dates log line").assertExists()
        onNodeWithText("/a.jpg").assertExists()
        onNodeWithText("before: a.jpg, 1 KB, modified 2019-05-04 12:00:00").assertExists()
        onNodeWithText("after: a.jpg, 1 KB, modified 2019-05-04 12:01:00").assertExists()
        onNodeWithText("/b.jpg: FAILED: boom").assertExists()
        onNodeWithText("Failed (1)").assertExists()
        onNodeWithText("Done (1)").assertExists()
        onNodeWithText("Clear history").assertDoesNotExist()
    }

    @Test
    fun `Back returns from a run page to the list`() = runComposeUiTest {
        threeRuns()
        screen()
        onNodeWithText("Dates").performClick()
        waitForIdle()

        onNodeWithText("Back").performClick()
        waitForIdle()

        onNodeWithText("Failed (1)").assertExists()
        onNodeWithText("Back").assertDoesNotExist()
        assertNull(controller.view.value.selected)
    }

    @Test
    fun `Clear history asks first, and Cancel keeps every run`() = runComposeUiTest {
        threeRuns()
        screen()

        onNodeWithText("Clear history").assertIsEnabled().performClick()
        waitForIdle()

        onNodeWithText("Forget 3 ended runs? A run that is waiting or running stays.").assertExists()
        assertEquals(3, store.runs().size)
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        onNodeWithText("Forget 3 ended runs? A run that is waiting or running stays.").assertDoesNotExist()
        assertEquals(3, store.runs().size)
    }

    @Test
    fun `Forget clears the ended runs and leaves a waiting one`() = runComposeUiTest {
        ended("Old", RunState.Done, "Old apply: 1 done")
        store.begin("Waiting job", T0)
        screen()

        onNodeWithText("Clear history").performClick()
        waitForIdle()
        onNodeWithText("Forget 1 ended run? A run that is waiting or running stays.").assertExists()
        onNodeWithText("Forget").performClick()
        waitForIdle()

        assertEquals(listOf("Waiting job"), store.runs().map { it.action })
        onNodeWithText("Waiting (1)").assertExists()
        onNodeWithText("Done (1)").assertDoesNotExist()
        onNodeWithText("Clear history?").assertDoesNotExist()
    }
}
