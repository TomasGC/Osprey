package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.testing.TimedTest
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import app.osprey.history.HistoryController
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunState
import app.osprey.history.UNDO_WINDOW_MS
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

    private fun ComposeUiTest.click(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.check(action: String) {
        onNodeWithContentDescription("Select $action").performClick()
        waitForIdle()
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
        onNodeWithText("Clear successful").assertIsNotEnabled()
    }

    @Test
    fun `a waiting run alone leaves Clear successful disabled`() = runComposeUiTest {
        store.begin("Dates", T0)

        screen()

        onNodeWithText("Waiting (1)").assertExists()
        onNodeWithText("Clear successful").assertIsNotEnabled()
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
        onNodeWithText("Clear successful").assertDoesNotExist()
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
    fun `a failed run alone leaves Clear successful disabled`() = runComposeUiTest {
        ended("Dates", RunState.Failed, "Dates apply: 1 failed")

        screen()

        onNodeWithText("Clear successful").assertIsNotEnabled()
    }

    @Test
    fun `Clear successful hides the done runs, keeps the others and offers an Undo`() = runComposeUiTest {
        threeRuns()
        screen()

        onNodeWithText("Clear successful").assertIsEnabled()
        click("Clear successful")

        onNodeWithText("2 runs removed").assertExists()
        onNodeWithText("Undo").assertExists()
        onNodeWithText("Done (2)").assertDoesNotExist()
        onNodeWithText("Failed (1)").assertExists()
        assertEquals(3, store.runs().size)
    }

    @Test
    fun `Undo brings the runs back and the store keeps them`() = runComposeUiTest {
        threeRuns()
        screen()
        click("Clear successful")

        click("Undo")

        onNodeWithText("Done (2)").assertExists()
        onNodeWithText("Undo").assertDoesNotExist()
        mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1)
        assertEquals(3, store.runs().size)
    }

    @Test
    fun `the store forgets the runs once the undo window has passed`() = runComposeUiTest {
        threeRuns()
        screen()
        click("Clear successful")

        mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1000)
        assertEquals(3, store.runs().size)
        mainClock.advanceTimeBy(2000)
        waitForIdle()

        assertEquals(listOf("Dates"), store.runs().map { it.action })
        onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `a second removal restarts the window`() = runComposeUiTest {
        threeRuns()
        screen()
        check("Reorg")
        click("Clear selected (1)")
        mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1000)

        check("Rename")
        click("Clear selected (1)")
        mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1000)

        assertEquals(listOf("Dates", "Rename"), store.runs().map { it.action }.sorted())
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        assertEquals(listOf("Dates"), store.runs().map { it.action })
    }

    @Test
    fun `the close button makes the removal final at once`() = runComposeUiTest {
        threeRuns()
        screen()
        click("Clear successful")

        onNodeWithContentDescription("Close notice").performClick()
        waitForIdle()

        assertEquals(listOf("Dates"), store.runs().map { it.action })
        onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `a checkbox checks a run and the button counts the checked ones`() = runComposeUiTest {
        threeRuns()
        screen()

        check("Reorg")
        onNodeWithText("Clear selected (1)").assertIsEnabled()
        check("Dates")

        onNodeWithText("Clear selected (2)").assertExists()
        check("Dates")
        onNodeWithText("Clear selected (1)").assertExists()
        check("Reorg")
        onNodeWithText("Clear successful").assertExists()
    }

    @Test
    fun `a click on a run checks it while another one is checked, and opens it otherwise`() = runComposeUiTest {
        threeRuns()
        screen()
        check("Reorg")

        click("Rename")

        onNodeWithText("Clear selected (2)").assertExists()
        assertNull(controller.view.value.selected)
    }

    @Test
    fun `Clear selected removes the checked runs only, even a failed one`() = runComposeUiTest {
        threeRuns()
        screen()
        check("Dates")

        click("Clear selected (1)")

        onNodeWithText("Run removed").assertExists()
        onNodeWithText("Failed (1)").assertDoesNotExist()
        onNodeWithText("Done (2)").assertExists()
        mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1)
        assertEquals(listOf("Rename", "Reorg"), store.runs().map { it.action }.sorted())
    }

    @Test
    fun `the Delete key removes the checked runs`() = runComposeUiTest {
        threeRuns()
        screen()
        check("Reorg")

        onRoot().performKeyInput { pressKey(Key.Delete) }
        waitForIdle()

        onNodeWithText("Run removed").assertExists()
        onNodeWithText("Done (1)").assertExists()
    }

    @Test
    fun `the Delete key does nothing while no run is checked`() = runComposeUiTest {
        threeRuns()
        screen()

        onRoot().performKeyInput { pressKey(Key.Delete) }
        waitForIdle()

        onNodeWithText("Undo").assertDoesNotExist()
        onNodeWithText("Done (2)").assertExists()
    }

    @Test
    fun `a waiting or running run has no checkbox`() = runComposeUiTest {
        store.begin("Waiting job", T0)
        ended("Old", RunState.Done, "Old apply: 1 done")
        screen()

        onNodeWithContentDescription("Select Waiting job").assertDoesNotExist()
        onNodeWithContentDescription("Select Old").assertExists()
    }

    @Test
    fun `leaving the screen makes a pending removal final`() = runComposeUiTest {
        threeRuns()
        val shown = mutableStateOf(true)
        controller.refresh()
        setContent { if (shown.value) HistoryScreen(controller, ZoneOffset.UTC) }
        waitForIdle()
        click("Clear successful")

        shown.value = false
        waitForIdle()

        assertEquals(listOf("Dates"), store.runs().map { it.action })
    }
}
