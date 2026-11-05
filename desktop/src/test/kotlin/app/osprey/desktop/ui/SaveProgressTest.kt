package app.osprey.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import app.osprey.viewer.TurnSaveResult
import app.osprey.viewer.ViewTurns
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val HOLD_MS = 120_000L
private val ONE_PIXEL = PixelBuffer(1, 1, intArrayOf(0xFF336699.toInt()))

/**
 * The viewer while a turn is being saved (#74): a saver held open by a latch stands for a big PNG, so the indicator,
 * the button and the outcome can be looked at in the middle of the save and after it, with the viewer open or closed.
 * The screen is asserted after `waitForIdle`, never polled: a `waitUntil` that cannot succeed runs for minutes on the
 * test clock, so a broken build would hang instead of failing.
 */
@OptIn(ExperimentalTestApi::class)
class SaveProgressTest : TimedTest() {

    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private var outcome: () -> TurnSaveResult = { TurnSaveResult.Saved }
    private val turns = ViewTurns(
        saveTurn = { _, _, _ ->
            started.countDown()
            release.await(HOLD_MS, TimeUnit.MILLISECONDS)
            outcome()
        },
    )
    private val photo = PathHandle(Path.of("IMG_0001.jpg"))
    private var open by mutableStateOf(true)

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer = ONE_PIXEL
    }

    /** A test that ends with the save still held lets it go, so no thread waits on after the test. */
    @After
    fun letTheSaverGo() = release.countDown()

    private fun ComposeUiTest.viewer() = setContent {
        if (open) {
            MediaPreview(
                reader, turns, photo, "IMG_0001.jpg", checked = false, checkLabel = "Keep",
                onCheckedChange = {}, onClose = {},
            )
        }
    }

    private fun ComposeUiTest.saveButton() = onNodeWithContentDescription("Save turn")

    private fun ComposeUiTest.startSave() {
        onNodeWithContentDescription("Rotate right").performClick()
        saveButton().performClick()
        waitUntil(timeoutMillis = WAIT_MS) { started.count == 0L }
        waitForIdle()
    }

    /** Lets the held save finish, then lets the screen catch up: the asserts that follow need no polling. */
    private fun ComposeUiTest.finishSave() {
        release.countDown()
        waitUntil(timeoutMillis = WAIT_MS) { turns.savingNow.value.isEmpty() }
        waitForIdle()
    }

    @Test
    fun `no indicator shows before a save`() = runComposeUiTest {
        viewer()

        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
    }

    @Test
    fun `a save that is running shows its progress and holds the button`() = runComposeUiTest {
        viewer()

        startSave()

        onNodeWithText(ViewTurns.SAVING).assertExists()
        saveButton().assertIsNotEnabled()
        release.countDown()
    }

    @Test
    fun `the indicator goes when the save works`() = runComposeUiTest {
        viewer()
        startSave()
        onNodeWithText(ViewTurns.SAVING).assertExists()

        finishSave()

        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
        assertEquals(0, turns.turnsOf(photo))
    }

    @Test
    fun `the indicator goes when the save fails and says why`() = runComposeUiTest {
        outcome = { TurnSaveResult.Failed("busy") }
        viewer()
        startSave()

        finishSave()

        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
        onNodeWithText("Could not save the turn: busy").assertExists()
        saveButton().assertIsEnabled()
    }

    @Test
    fun `the indicator goes when the saver throws and the viewer carries on`() = runComposeUiTest {
        outcome = { error("disk gone") }
        viewer()
        startSave()

        finishSave()

        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
        onNodeWithText("Could not save the turn: disk gone").assertExists()
        saveButton().assertIsEnabled()
    }

    @Test
    fun `the indicator goes when the save is refused and says why`() = runComposeUiTest {
        outcome = { TurnSaveResult.Refused("not this one") }
        viewer()
        startSave()

        finishSave()

        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
        onNodeWithText("not this one").assertExists()
    }

    @Test
    fun `closing the viewer in the middle of a save lets the save finish`() = runComposeUiTest {
        viewer()
        startSave()

        open = false
        waitForIdle()
        finishSave()

        assertEquals(0, turns.turnsOf(photo))
        assertEquals(1, turns.revisionOf(photo))
    }

    @Test
    fun `reopening the viewer in the middle of a save still shows it and cannot save twice`() = runComposeUiTest {
        viewer()
        startSave()

        open = false
        waitForIdle()
        open = true
        waitForIdle()

        onNodeWithText(ViewTurns.SAVING).assertExists()
        saveButton().assertIsNotEnabled()
        release.countDown()
    }

    @Test
    fun `a save that failed after the viewer was closed is told when it is opened again`() = runComposeUiTest {
        outcome = { TurnSaveResult.Failed("busy") }
        viewer()
        startSave()

        open = false
        waitForIdle()
        finishSave()
        open = true
        waitForIdle()

        onNodeWithText("Could not save the turn: busy").assertExists()
        onNodeWithText(ViewTurns.SAVING).assertDoesNotExist()
    }
}
