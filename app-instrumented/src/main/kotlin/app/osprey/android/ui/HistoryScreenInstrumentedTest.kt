package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("Clear successful").assertIsNotEnabled()
    }

    @Test
    fun aWaitingRunAloneLeavesClearSuccessfulDisabled() {
        store.begin("Dates", T0)

        screen()

        composeRule.onNodeWithText("Waiting (1)").assertExists()
        composeRule.onNodeWithText("Clear successful").assertIsNotEnabled()
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
        composeRule.onNodeWithText("Failed (1)").assertExists()
        composeRule.onNodeWithText("Done (1)").assertExists()
        composeRule.onNodeWithText("Skipped (0)").assertDoesNotExist()
        composeRule.onNodeWithText("Clear successful").assertDoesNotExist()
    }

    @Test
    fun theFailedFilesOfARunAreListedAboveTheDoneOnes() {
        threeRuns()
        screen()

        click("Dates")

        val failedTop = composeRule.onNodeWithText("Failed (1)").fetchSemanticsNode().positionInRoot.y
        val doneTop = composeRule.onNodeWithText("Done (1)").fetchSemanticsNode().positionInRoot.y
        assertTrue("the failed group is above the done group", failedTop < doneTop)
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

    private fun startSelecting(action: String) {
        composeRule.onNodeWithText(action).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun aFailedRunAloneLeavesClearSuccessfulDisabled() {
        ended("Dates", RunState.Failed, "Dates apply: 1 failed")

        screen()

        composeRule.onNodeWithText("Clear successful").assertIsNotEnabled()
    }

    @Test
    fun clearSuccessfulHidesTheDoneRunsKeepsTheOthersAndOffersAnUndo() {
        threeRuns()
        screen()

        composeRule.onNodeWithText("Clear successful").assertIsEnabled()
        click("Clear successful")

        composeRule.onNodeWithText("2 runs removed").assertExists()
        composeRule.onNodeWithText("Undo").assertExists()
        composeRule.onNodeWithText("Done (2)").assertDoesNotExist()
        composeRule.onNodeWithText("Failed (1)").assertExists()
        assertEquals(3, store.runs().size)
    }

    @Test
    fun undoBringsTheRunsBackAndTheStoreKeepsThem() {
        threeRuns()
        screen()
        click("Clear successful")

        click("Undo")

        composeRule.onNodeWithText("Done (2)").assertExists()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1000)
        assertEquals(3, store.runs().size)
    }

    @Test
    fun theStoreForgetsTheRunsOnceTheUndoWindowHasPassed() {
        threeRuns()
        screen()
        click("Clear successful")

        composeRule.mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1000)
        assertEquals(3, store.runs().size)
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()

        assertEquals(listOf("Dates"), store.runs().map { it.action })
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun aLongPressChecksARunAndTheButtonCountsTheCheckedOnes() {
        threeRuns()
        screen()

        composeRule.onNodeWithText("Reorg").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Clear selected (1)").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Select Reorg").assertIsOn()
        composeRule.onNodeWithContentDescription("Select Rename").assertIsOff()
        assertNull(controller.view.value.selected)
    }

    @Test
    fun aTapChecksOrUnchecksWhileARunIsCheckedAndOpensOtherwise() {
        threeRuns()
        screen()
        composeRule.onNodeWithText("Reorg").performTouchInput { longClick() }
        composeRule.waitForIdle()

        click("Rename")
        composeRule.onNodeWithText("Clear selected (2)").assertExists()
        click("Reorg")
        click("Rename")

        composeRule.onNodeWithText("Clear successful").assertExists()
        click("Rename")
        assertEquals("Rename", controller.view.value.selected?.action)
    }

    @Test
    fun clearSelectedRemovesTheCheckedRunsOnlyEvenAFailedOne() {
        threeRuns()
        screen()
        startSelecting("Dates")

        click("Clear selected (1)")

        composeRule.onNodeWithText("Run removed").assertExists()
        composeRule.onNodeWithText("Failed (1)").assertDoesNotExist()
        composeRule.onNodeWithText("Done (2)").assertExists()
        composeRule.mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1000)
        assertEquals(listOf("Rename", "Reorg"), store.runs().map { it.action }.sorted())
    }

    @Test
    fun backEndsTheSelectionBeforeAnythingElse() {
        threeRuns()
        screen()
        startSelecting("Reorg")

        back()

        composeRule.onNodeWithText("Clear successful").assertExists()
        composeRule.onNodeWithContentDescription("Select Reorg").assertDoesNotExist()
    }

    @Test
    fun aSwipeToTheRightRemovesARunWithAnUndo() {
        threeRuns()
        screen()

        composeRule.onNodeWithText("Reorg").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Run removed").assertExists()
        composeRule.onNodeWithText("Reorg").assertDoesNotExist()
        assertEquals(3, store.runs().size)
        click("Undo")
        composeRule.onNodeWithText("Reorg").assertExists()
    }

    @Test
    fun aWaitingRunDoesNotSwipeAwayAndHasNoCheckbox() {
        store.begin("Waiting job", T0)
        screen()

        composeRule.onNodeWithText("Waiting job").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Waiting job").assertExists()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Select Waiting job").assertDoesNotExist()
    }

    @Test
    fun swipingTheNoticeAwayMakesTheRemovalFinalAtOnce() {
        threeRuns()
        screen()
        click("Clear successful")

        composeRule.onNodeWithText("2 runs removed").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(listOf("Dates"), store.runs().map { it.action })
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }
}
