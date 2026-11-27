package app.osprey.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.ui.OspreyApp
import app.osprey.fixtures.Photos
import app.osprey.jobs.JobState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val SCAN_WAIT_SECONDS = 90L

/**
 * A Visual Dupes scan in the real desktop app over real HEICs (#87): every HEIC of the private fixtures twice, and the
 * PNG copies of some, decoded by the real ffmpeg several at a time. Each photo groups with its own copies and with
 * nothing else, so a file left out of the scan, or two shots wrongly joined, changes the groups.
 */
@OptIn(ExperimentalTestApi::class)
class VisualDupeScanEndToEndTest : TimedTest() {

    private lateinit var root: Path
    private lateinit var expected: Set<Set<String>>

    @Before
    fun setUp() {
        val fixtures = Photos.need()
        root = Files.createTempDirectory("osprey-e2e-scan")
        val year = Files.createDirectories(root.resolve("Library").resolve("2021"))
        val pngs = Photos.png(fixtures)
        val heics = Photos.heic(fixtures)
        (heics + pngs).forEach { Files.copy(it, year.resolve(it.fileName.toString())) }
        heics.forEach { Files.copy(it, year.resolve("twin of ${it.fileName}")) }
        val withPng = pngs.map { it.fileName.toString().removeSuffix(".png") }.toSet()
        expected = heics.map { heic ->
            val name = heic.fileName.toString()
            val subject = name.removeSuffix(".heic")
            setOfNotNull(name, "twin of $name", "$subject.png".takeIf { subject in withPng })
        }.toSet()
        val config = JSONObject()
            .put("output_path", root.resolve("Library").toString())
            .put("input_folders", JSONArray())
        Files.writeString(root.resolve("config.json"), config.toString())
        System.setProperty("osprey.config.path", root.resolve("config.json").toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("osprey.config.path")
        if (::root.isInitialized) root.toFile().deleteRecursively()
    }

    @Test
    fun `the real app scans real HEICs and groups each with its PNG copy`() = runComposeUiTest {
        val state = desktopAppState()
        state.start()
        setContent { OspreyApp(state) }

        onNodeWithText("Visual Dupes").performClick()
        onNodeWithText("Scan (can take a minute or two)").performClick()
        val finished = runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(SCAN_WAIT_SECONDS)) {
                state.jobs.entries.first { entries ->
                    entries.isNotEmpty() && entries.all { it.state is JobState.Finished }
                }
            }
        }
        waitForIdle()

        val summary = (finished.single().state as JobState.Finished).outcome.summary
        assertEquals("Visual Dupes 2021 scan: ${expected.size} groups", summary)
        onNodeWithText("GROUPS").assertIsDisplayed()
        val grouped = state.visual.groupsIn(checkNotNull(state.yearHandle()))
            .map { group -> group.members.map { it.name }.toSet() }
            .toSet()
        assertEquals(expected, grouped)
    }
}
