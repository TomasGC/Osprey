package app.osprey.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.ui.OspreyApp
import app.osprey.fixtures.Photos
import app.osprey.history.openHistory
import app.osprey.jobs.JobState
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 30_000L
private val VIDEOS = listOf(
    "20190629_151754.mp4",
    "20190629_151754_1.mp4", // the same video again: its standard name collides with the first one's
    "20190629_153748.mp4",
    "VID-20180314-WA0073.mp4", // no container date, so the name is all there is on the next scan
)

/**
 * Standardize through the whole desktop app on real phone videos (#84): the real shell, a real [AppState] over a temp
 * library, real files and the real ffprobe reader, driven by clicks. The camera videos were shot at UTC+2 and their
 * names are 17 s and 11 s off the container: on a machine at that offset the container's time stays, anywhere else
 * the name's does. One of them is there twice (the names collide) and one is a WhatsApp clip with no container date.
 * Either way a second Scan, a person's next visit, must find nothing left to rename and nothing unfixable.
 */
@OptIn(ExperimentalTestApi::class)
class StandardizeEndToEndTest : TimedTest() {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val year get() = library.resolve("2021")

    @Before
    fun setUp() {
        val fixtures = Photos.need()
        assumeTrue("ffprobe not on PATH", isOnPath("ffprobe"))
        root = Files.createTempDirectory("osprey-standardize-e2e")
        Files.createDirectories(year)
        for (name in VIDEOS) {
            Files.copy(Photos.mp4(fixtures).single { it.fileName.toString() == name }, year.resolve(name))
        }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.toFile().deleteRecursively()
    }

    private fun isOnPath(tool: String): Boolean = try {
        ProcessBuilder(tool, "-version").redirectErrorStream(true).start().let {
            it.inputStream.readBytes()
            it.waitFor() == 0
        }
    } catch (ignored: java.io.IOException) {
        false
    }

    private fun launch() = AppState(
        DesktopFileSystemRepository(),
        DesktopMediaMetadataReader(),
        FakeSettingsStore(),
        history = openHistory(root.resolve("history").toFile()),
    ).also { it.openLibrary(library) }

    private fun waitForJobs(state: AppState) = runBlocking {
        withTimeout(WAIT_MS) {
            state.jobs.entries.first { entries ->
                entries.isNotEmpty() && entries.all { it.state is JobState.Finished }
            }
        }
    }

    private fun ComposeUiTest.click(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    /** The summary line and the log both say it, so one node is not what is asked: at least one is. */
    private fun ComposeUiTest.assertShown(text: String) = assertTrue(
        "'$text' is not on the screen",
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(),
    )

    private fun ComposeUiTest.scan(state: AppState) {
        click("Scan")
        waitForJobs(state)
        waitForIdle()
    }

    @Test
    fun `Standardize names two real phone videos through the whole app and the next scan finds nothing`() =
        runComposeUiTest {
            val shotZone = ZoneId.systemDefault().rules.getOffset(Instant.parse("2019-06-29T13:18:11Z")) ==
                ZoneOffset.ofHours(2)
            val (first, second) = if (shotZone) {
                "VID_2019-06-29_15-18-11" to "VID_2019-06-29_15-37-59"
            } else {
                "VID_2019-06-29_15-17-54" to "VID_2019-06-29_15-37-48"
            }
            val expected = listOf("$first.mp4", "${first}__dup2.mp4", "$second.mp4", "VID_2018-03-14_00-00-00.mp4")
                .sorted()
            val state = launch()
            setContent { OspreyApp(state) }

            click("Standardize")
            scan(state)
            assertShown("count=4  unfixable=0")
            click("Apply")
            waitForJobs(state)
            waitForIdle()
            assertEquals(expected, year.toFile().list()!!.sorted())
            scan(state)

            assertShown("count=0  unfixable=0")
            onNodeWithText("Apply").assertIsNotEnabled()
            assertEquals(expected, year.toFile().list()!!.sorted())
        }
}
