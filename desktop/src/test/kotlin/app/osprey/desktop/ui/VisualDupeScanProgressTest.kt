package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.ActionTab
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.scanWorkers
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WAIT_SECONDS = 20L
private const val SIDE = 8

/**
 * A Visual Dupes scan decoding several photos at once, as the app shows it (#87): one line of progress per photo
 * started, and Stop. The reader finishes a decode only when the test lets it, so nothing here depends on timing.
 */
@OptIn(ExperimentalTestApi::class)
class VisualDupeScanProgressTest : TimedTest() {

    /** Each decode waits for one of [permits]; [busy] gains one for every decode that started. */
    private class HeldReader : MediaMetadataReader {
        val permits = Semaphore(0)
        val busy = Semaphore(0)

        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer {
            busy.release()
            check(permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) { "never released" }
            return PixelBuffer(SIDE, SIDE, IntArray(SIDE * SIDE) { it })
        }
    }

    private val workers = scanWorkers()
    private val photos = workers + 2
    private val reader = HeldReader()
    private lateinit var library: Path

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-scan-progress")
        val year = Files.createDirectory(library.resolve("2021"))
        repeat(photos) { Files.writeString(year.resolve("photo$it.jpg"), "photo $it") }
    }

    @After
    fun tearDown() {
        // A test that failed must not leave its job waiting.
        reader.permits.release(photos)
        library.toFile().deleteRecursively()
    }

    private fun awaitStarted(count: Int) = assertTrue(reader.busy.tryAcquire(count, WAIT_SECONDS, TimeUnit.SECONDS))

    private fun awaitFinished(state: AppState) = runBlocking {
        withTimeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)) {
            state.jobs.entries.first { entries -> entries.all { it.state is JobState.Finished } }
        }
    }

    /** The files are listed in an order the platform chooses, so only the count of the progress line is matched. */
    private fun ComposeUiTest.assertProgress(number: Int) {
        waitForIdle()
        onNodeWithText("$number of $photos: ", substring = true).assertIsDisplayed()
    }

    @Test
    fun `progress counts each photo and Stop ends the scan on screen`() = runComposeUiTest {
        val state = AppState(DesktopFileSystemRepository(), reader, FakeSettingsStore())
        state.openLibrary(library)
        state.selectedTab = ActionTab.VISUAL
        setContent { OspreyApp(state) }

        onNodeWithText("Scan (can take a minute or two)").performClick()
        awaitStarted(workers)
        assertProgress(1)

        reader.permits.release(1) // one finishes, the next starts
        awaitStarted(1)
        assertProgress(2)

        onNodeWithText("Stop").performClick()
        waitForIdle()
        onNodeWithText("Stopping after this file...").assertIsDisplayed()

        reader.permits.release(photos)
        awaitFinished(state)
        waitForIdle()
        onNodeWithText("Visual Dupes 2021 scan: stopped").assertIsDisplayed()
        assertEquals("nothing may start after Stop", 0, reader.busy.availablePermits())
    }
}
