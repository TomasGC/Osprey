package app.osprey.desktop.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.testing.toJpeg
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileResult
import app.osprey.history.UNDO_WINDOW_MS
import app.osprey.history.openHistory
import app.osprey.jobs.JobState
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
class HistoryEndToEndTest : TimedTest() {

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
    private fun launch(fs: FileSystemRepository = DesktopFileSystemRepository()) =
        AppState(fs, DesktopMediaMetadataReader(), FakeSettingsStore(), history = openHistory(historyDir))
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
        onNodeWithText("2021/sub/a.jpg: moved up as a.jpg").assertExists()
        click("Back")
        onNodeWithText("Done (2)").assertExists()
    }

    @Test
    fun `Clear successful removes the runs and their files once the undo window has passed, and Undo keeps them`() =
        runComposeUiTest {
            val state = launch()
            setContent { OspreyApp(state) }
            flattenThroughTheScreen(state)
            click("🕘")

            click("Clear successful")
            onNodeWithText("2 runs removed").assertExists()
            click("Undo")
            onNodeWithText("Done (2)").assertExists()
            assertEquals(2, historyDir.list()!!.size)
            click("Clear successful")
            mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1)
            waitForIdle()

            onNodeWithText("No runs yet.").assertExists()
            assertEquals(0, historyDir.list()!!.size)
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `a Save turn through the app state is recorded with the file before and after`() {
        val state = launch()
        val original = BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB).toJpeg(orientation = 6)
        val photo = Files.write(year.resolve("p.jpg"), original)
        val handle = PathHandle(photo)
        state.viewTurns.turn(handle, 1)

        state.viewTurns.save(handle, "p.jpg")

        val record = state.history.runs().first { it.action == "Save turn" }.files.single()
        assertEquals(FileResult.Done, record.result)
        assertEquals(sha256(original), record.before?.hash)
        assertEquals(sha256(Files.readAllBytes(photo)), record.after?.hash)
        assertEquals(original.size.toLong(), record.before?.size)
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

    @Test
    fun `a run that failed on a file is retried from its page and the retry is linked to it`() = runComposeUiTest {
        val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
        fs.failMove = { if ((it as PathHandle).path.fileName.toString() == "b.jpg") IOException("locked") else null }
        val state = launch(fs)
        setContent { OspreyApp(state) }
        flattenThroughTheScreen(state)
        click("🕘")
        onNode(hasText("Flatten 2021 apply") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("Retry failed (1)").assertExists()
        fs.failMove = { null }

        click("Retry failed (1)")
        waitForJobs(state)
        waitForIdle()

        val first = state.history.runs().first { it.action == "Flatten 2021 apply" }
        val retry = state.history.runs().first { it.action == "Flatten 2021 retry" }
        assertEquals(first.id, retry.origin?.retryOf)
        listOf("a", "b", "c").forEach { assertTrue(Files.exists(year.resolve("$it.jpg"))) }
        onAllNodesWithText("Back").assertCountEquals(0)
        onNode(hasText("Flatten 2021 retry") and hasClickAction()).assertExists()
    }

    @Test
    fun `a Trash delete that failed on a file asks on the History page before it is retried`() = runComposeUiTest {
        val folder = Files.createDirectories(library.resolve("Trash/2021/Dedupe"))
        listOf("a", "b").forEach { Files.writeString(folder.resolve("$it.jpg"), it) }
        val fs = FaultyFileSystemRepository(DesktopFileSystemRepository())
        fs.failDelete = { if ((it as PathHandle).path.fileName.toString() == "a.jpg") IOException("locked") else null }
        val state = launch(fs)
        val root = PathHandle(library)
        state.trash.refresh(root)
        state.trash.toggleTab()
        state.trash.requestDelete()
        state.trash.confirmDelete(root)
        waitForJobs(state)
        setContent { OspreyApp(state) }
        click("🕘")
        onNode(hasText("Trash delete") and hasClickAction()).performClick()
        waitForIdle()

        click("Retry failed (1)")
        onNodeWithText("Delete for good?").assertExists()
        click("Cancel")
        assertTrue(Files.exists(folder.resolve("a.jpg")))
        onNodeWithText("Retry failed (1)").assertExists()
        fs.failDelete = { null }
        click("Retry failed (1)")
        click("Delete")
        waitForJobs(state)
        waitForIdle()

        assertFalse(Files.exists(folder.resolve("a.jpg")))
        val first = state.history.runs().first { it.action == "Trash delete" }
        val retry = state.history.runs().first { it.action == "Trash delete retry" }
        assertEquals(first.id, retry.origin?.retryOf)
    }

    @Test
    fun `a Trash restore of two same-name files through the Trash screen shows the suffixed name in the History`() =
        runComposeUiTest {
            listOf("Dedupe" to "dedupe", "VisualDupes" to "visual").forEach { (action, text) ->
                val folder = Files.createDirectories(library.resolve("Trash/2021/$action"))
                Files.writeString(folder.resolve("IMG_001.jpg"), text)
            }
            val state = launch()
            setContent { OspreyApp(state) }

            click("🗑")
            click("Select all in this tab")
            click("VisualDupes (1)")
            click("Select all in this tab")
            click("Restore")
            waitForJobs(state)
            waitForIdle()

            onNode(hasText("RESTORED 2, failed 0", substring = true)).assertExists()
            assertEquals("dedupe", Files.readString(year.resolve("IMG_001.jpg")))
            assertEquals("visual", Files.readString(year.resolve("IMG_001_VisualDupes.jpg")))
            click("🕘")
            onNode(hasText("Trash restore") and hasClickAction()).performClick()
            waitForIdle()
            onNodeWithText("Trash/2021/Dedupe/IMG_001.jpg: restored").assertExists()
            onNodeWithText("Trash/2021/VisualDupes/IMG_001.jpg: restored as IMG_001_VisualDupes.jpg").assertExists()
        }
}
