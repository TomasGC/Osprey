package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val T0: Instant = Instant.parse("2019-05-04T12:00:00Z")

/**
 * The Android History screen (#79) with the real composable over an in-memory history. Like the desktop test, it
 * waits for the screen to be idle and then asserts, so a wrong screen fails at once instead of at a timeout.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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

    private fun screen() {
        controller.refresh()
        composeRule.setContent { HistoryScreen(controller, ZoneOffset.UTC) }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theRunsAreListedByStateWithTheirCountsAndALineEach() {
        threeRuns()

        screen()

        composeRule.onNodeWithText("Failed (1)").assertExists()
        composeRule.onNodeWithText("Done (2)").assertExists()
        composeRule.onNodeWithText("Dates").assertExists()
        composeRule.onNodeWithText("Dates apply: 1 done, 1 failed").assertExists()
        composeRule.onNodeWithText("2 files: 1 done, 1 failed").assertExists()
        composeRule.onAllNodesWithText("2019-05-04 12:00:00").assertCountEquals(3)
    }

    @Test
    fun anEmptyHistorySaysSoAndHasNothingToClear() {
        screen()

        composeRule.onNodeWithText("No runs yet.").assertExists()
        composeRule.onNodeWithText("Clear history").assertIsNotEnabled()
    }

    @Test
    fun aWaitingRunAloneLeavesClearHistoryDisabled() {
        store.begin("Dates", T0)

        screen()

        composeRule.onNodeWithText("Waiting (1)").assertExists()
        composeRule.onNodeWithText("Clear history").assertIsNotEnabled()
    }

    @Test
    fun aRunOpensOnItsPageWithItsSummaryLogFilesAndBeforeAndAfter() {
        threeRuns()
        screen()

        click("Dates")

        composeRule.onNodeWithText("Back").assertExists()
        composeRule.onNodeWithText("Failed").assertExists()
        composeRule.onNodeWithText("Dates apply: 1 done, 1 failed").assertExists()
        composeRule.onNodeWithText("2019-05-04 12:00:00 · took 4 s").assertExists()
        composeRule.onNodeWithText("2 files: 1 done, 1 failed").assertExists()
        composeRule.onNodeWithText("Dates log line").assertExists()
        composeRule.onNodeWithText("/a.jpg").assertExists()
        composeRule.onNodeWithText("before: a.jpg, 1 KB, modified 2019-05-04 12:00:00").assertExists()
        composeRule.onNodeWithText("after: a.jpg, 1 KB, modified 2019-05-04 12:01:00").assertExists()
        composeRule.onNodeWithText("/b.jpg: FAILED: boom").assertExists()
        composeRule.onNodeWithText("Failed (1)").assertDoesNotExist()
    }

    @Test
    fun backReturnsFromARunPageToTheList() {
        threeRuns()
        screen()
        click("Dates")

        click("Back")

        composeRule.onNodeWithText("Failed (1)").assertExists()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
        assertNull(controller.view.value.selected)
    }

    @Test
    fun theSystemBackButtonReturnsFromARunPageToTheList() {
        threeRuns()
        screen()
        click("Dates")

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Failed (1)").assertExists()
        assertNull(controller.view.value.selected)
    }

    @Test
    fun clearHistoryAsksFirstAndCancelKeepsEveryRun() {
        threeRuns()
        screen()

        composeRule.onNodeWithText("Clear history").assertIsEnabled()
        click("Clear history")

        val question = "Forget 3 ended runs? A run that is waiting or running stays."
        composeRule.onNodeWithText(question).assertExists()
        assertEquals(3, store.runs().size)
        click("Cancel")

        composeRule.onNodeWithText(question).assertDoesNotExist()
        assertEquals(3, store.runs().size)
    }

    @Test
    fun forgetClearsTheEndedRunsAndLeavesAWaitingOne() {
        ended("Old", RunState.Done, "Old apply: 1 done")
        store.begin("Waiting job", T0)
        screen()

        click("Clear history")
        composeRule.onNodeWithText("Forget 1 ended run? A run that is waiting or running stays.").assertExists()
        click("Forget")

        assertEquals(listOf("Waiting job"), store.runs().map { it.action })
        composeRule.onNodeWithText("Waiting (1)").assertExists()
        composeRule.onNodeWithText("Done (1)").assertDoesNotExist()
        composeRule.onNodeWithText("Clear history?").assertDoesNotExist()
    }
}
