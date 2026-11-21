package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.AndroidAppState
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FaultyFileSystemRepository
import app.osprey.history.FileResult
import app.osprey.history.RunRecorder
import app.osprey.history.UNDO_WINDOW_MS
import app.osprey.history.openHistory
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 10_000L

/**
  * The History (#79) through the whole Android app: the real shell with its drawer, a real [AndroidAppState] over the
  * core
 * fakes, and a real history in files. What a person does with a finger is done here with the test's clicks, the system
 * Back button goes through the activity's dispatcher, and a new launch is a new state over the same folder. Jobs are
 * awaited on the queue's own state, never by polling the screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryEndToEndInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var historyDir: File

    private val settings = object : SettingsStore {
        override fun load(): MediaSettings = MediaSettings()

        override fun save(settings: MediaSettings) = Unit

        override fun hasSavedSettings(): Boolean = false
    }

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        historyDir = File(cache, "history_e2e_${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        historyDir.deleteRecursively()
    }

    private fun seedFlattenLibrary(fs: FakeFileSystemRepository) {
        val sub = fs.addFolder(fs.addFolder(fs.root, "2021"), "sub")
        listOf("a", "b", "c").forEach { fs.addFile(sub, "$it.jpg", "photo $it".toByteArray()) }
    }

    /** A launch of the app over [fs] (default: 2021/sub/{a,b,c}.jpg), and the history opened from its folder. */
    private fun launch(
        fs: FakeFileSystemRepository = FakeFileSystemRepository().also(::seedFlattenLibrary),
        reader: FakeMediaMetadataReader = FakeMediaMetadataReader(),
    ): AndroidAppState {
        val history = openHistory(historyDir)
        // As OspreyApplication does: the queue records into the same history the screens read.
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.Default), RunRecorder(history))
        return AndroidAppState(fs, reader, settings, jobs, { null }, {}, history)
            .also { it.openLibrary(fs.root) }
    }

    /** A launch of the app whose move of b.jpg fails until `fs.failMove` is cleared. */
    private class Failing(val state: AndroidAppState, val fs: FaultyFileSystemRepository)

    private fun launchFailingOnB(): Failing {
        val fake = FakeFileSystemRepository()
        val sub = fake.addFolder(fake.addFolder(fake.root, "2021"), "sub")
        val files = listOf("a", "b", "c").associateWith { fake.addFile(sub, "$it.jpg", "photo $it".toByteArray()) }
        val fs = FaultyFileSystemRepository(fake)
        fs.failMove = { if (it == files.getValue("b")) IOException("locked") else null }
        val history = openHistory(historyDir)
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.Default), RunRecorder(history))
        val state = AndroidAppState(fs, FakeMediaMetadataReader(), settings, jobs, { null }, {}, history)
        state.libraryReference = "content://library"
        state.openLibrary(fake.root)
        return Failing(state, fs)
    }

    private fun waitForJobs(state: AndroidAppState) = runBlocking {
        withTimeout(WAIT_MS) {
            state.jobs.entries.first { entries ->
                entries.isNotEmpty() && entries.all { it.state is JobState.Finished }
            }
        }
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun openDrawer() = click("☰")

    private fun flattenThroughTheScreen(state: AndroidAppState) {
        openDrawer()
        click("Flatten")
        click("Scan")
        waitForJobs(state)
        composeRule.waitForIdle()
        click("Apply")
        waitForJobs(state)
        composeRule.waitForIdle()
    }

    private fun openHistoryFromTheDrawer() {
        openDrawer()
        composeRule.onNodeWithText("🕘 History").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openTrashFromTheDrawer() {
        openDrawer()
        composeRule.onNodeWithText("🗑 Trash").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun aFlattenRunOnAYearGoesThroughTheWholeAppAndIntoTheHistory() {
        val state = launch()
        composeRule.setContent { OspreyApp(state) }

        flattenThroughTheScreen(state)

        composeRule.onNodeWithText("Flatten 2021 apply: 3 done").assertExists()
        composeRule.onNodeWithText("Flatten 2021 scan: count=3").assertExists()
        openHistoryFromTheDrawer()
        composeRule.onNodeWithText("Done (2)").assertExists()
        composeRule.onNode(hasText("Flatten 2021 apply") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2021/sub/a.jpg: moved up as a.jpg").assertExists()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done (2)").assertExists()
    }

    @Test
    fun aStandardizeRunLeavesAVideoShotInAnotherTimezoneAloneOnTheNextScan() {
        val fs = FakeFileSystemRepository()
        val year = fs.addFolder(fs.root, "2021")
        val video = fs.addFile(year, "VID_20200710_134500.mp4")
        // Two photos with no metadata whose standard names collide: the next scan must leave them alone too.
        fs.addFile(year, "IMG_20170425_152602.jpg")
        fs.addFile(year, "IMG_20170425_152602_1.jpg")
        val reader = FakeMediaMetadataReader()
        val container = utcToLocal(LocalDateTime.of(2020, 7, 10, 8, 0, 0))
        reader.setCaptureDateTime(video, MediaDate(container, MediaDateSource.VIDEO_METADATA))
        val state = launch(fs, reader)
        composeRule.setContent { OspreyApp(state) }

        openDrawer()
        click("Standardize")
        click("Scan")
        waitForJobs(state)
        composeRule.waitForIdle()
        click("Apply")
        waitForJobs(state)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Standardize 2021 apply: 3 done").assertExists()
        click("Scan")
        waitForJobs(state)
        composeRule.waitForIdle()

        assertEquals(
            listOf("IMG_2017-04-25_15-26-02.jpg", "IMG_2017-04-25_15-26-02__dup2.jpg", "VID_2020-07-10_13-45-00.mp4"),
            fs.list(year).map { it.name }.sorted(),
        )
        assertTrue(
            "the second scan does not say there is nothing left",
            composeRule.onAllNodesWithText("count=0  unfixable=0", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test
    fun clearSuccessfulRemovesTheRunsAndTheirFilesOnceTheUndoWindowHasPassedAndUndoKeepsThem() {
        val state = launch()
        composeRule.setContent { OspreyApp(state) }
        flattenThroughTheScreen(state)
        openHistoryFromTheDrawer()

        click("Clear successful")
        composeRule.onNodeWithText("2 runs removed").assertExists()
        click("Undo")
        composeRule.onNodeWithText("Done (2)").assertExists()
        assertEquals(2, historyDir.list()!!.size)
        click("Clear successful")
        composeRule.mainClock.advanceTimeBy(UNDO_WINDOW_MS + 1000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No runs yet.").assertExists()
        assertEquals(0, historyDir.list()!!.size)
    }

    @Test
    fun aSaveTurnThroughTheAppStateIsRecordedWithTheFileAsItWas() {
        val state = launch()
        val content = "not a jpeg".toByteArray()
        val photo = (state.fs as FakeFileSystemRepository).addFile(state.yearHandle()!!, "p.jpg", content)
        state.viewTurns.turn(photo, 1)

        state.viewTurns.save(photo, "p.jpg")

        val record = state.history.runs().first { it.action == "Save turn" }.files.single()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        assertEquals(FileResult.Failed, record.result)
        assertEquals(sha256, record.before?.hash)
        assertEquals(content.size.toLong(), record.before?.size)
        assertNull(record.after)
    }

    @Test
    fun aNewLaunchShowsTheRunsOfTheLastOneAndClosesARunTheAppDiedIn() {
        val first = launch()
        val year = first.yearHandle()!!
        first.flatten.onScan(year, "2021")
        waitForJobs(first)
        first.flatten.onApply(year, "2021")
        waitForJobs(first)
        // What a killed app leaves behind: a run that began and never ended.
        File(historyDir, "99.jsonl").writeText(
            """{"t":"run","id":99,"action":"Flatten 2021 scan","at":1793737437979}""" + "\n" +
                """{"t":"start","at":1793737437986}""" + "\n",
        )

        val second = launch()
        composeRule.setContent { OspreyApp(second) }
        openHistoryFromTheDrawer()

        composeRule.onNodeWithText("Interrupted (1)").assertExists()
        composeRule.onNodeWithText("Done (2)").assertExists()
    }

    @Test
    fun aRunThatFailedOnAFileIsRetriedFromItsPageAndTheRetryIsLinkedToIt() {
        val failing = launchFailingOnB()
        val state = failing.state
        composeRule.setContent { OspreyApp(state) }
        flattenThroughTheScreen(state)
        openHistoryFromTheDrawer()
        composeRule.onNode(hasText("Flatten 2021 apply") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Retry failed (1)").assertExists()
        failing.fs.failMove = { null }

        click("Retry failed (1)")
        waitForJobs(state)
        composeRule.waitForIdle()

        val first = state.history.runs().first { it.action == "Flatten 2021 apply" }
        val retry = state.history.runs().first { it.action == "Flatten 2021 retry" }
        assertEquals(first.id, retry.origin?.retryOf)
        val names = failing.fs.list(state.yearHandle()!!).map { it.name }
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), names.filter { it.endsWith(".jpg") }.sorted())
        composeRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeRule.onNode(hasText("Flatten 2021 retry") and hasClickAction()).assertExists()
    }

    @Test
    fun aFailedRunHasNoRetryOnceAnotherYearIsSelected() {
        val state = launchFailingOnB().state
        composeRule.setContent { OspreyApp(state) }
        flattenThroughTheScreen(state)
        composeRule.runOnUiThread { state.selectedYear = "2020" }
        composeRule.waitForIdle()
        openHistoryFromTheDrawer()

        composeRule.onNode(hasText("Flatten 2021 apply") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("2021/sub/b.jpg", substring = true)).assertExists()
        composeRule.onAllNodesWithText("Retry failed (1)").assertCountEquals(0)
    }

    @Test
    fun aTrashRestoreOfTwoSameNameFilesThroughTheTrashScreenShowsTheSuffixedNameInTheHistory() {
        val fs = FakeFileSystemRepository()
        val year = fs.addFolder(fs.addFolder(fs.root, "Trash"), "2021")
        fs.addFile(fs.addFolder(year, "Dedupe"), "IMG_001.jpg", "dedupe".toByteArray())
        fs.addFile(fs.addFolder(year, "VisualDupes"), "IMG_001.jpg", "visual".toByteArray())
        val state = launch(fs)
        composeRule.setContent { OspreyApp(state) }

        openTrashFromTheDrawer()
        click("Select all in this tab")
        click("VisualDupes (1)")
        click("Select all in this tab")
        click("Restore")
        waitForJobs(state)
        composeRule.waitForIdle()

        composeRule.onNode(hasText("RESTORED 2, failed 0", substring = true)).assertExists()
        val names = fs.list(fs.list(fs.root).single { it.name == "2021" }.handle).map { it.name }
        assertEquals(listOf("IMG_001.jpg", "IMG_001_VisualDupes.jpg"), names.sorted())
        openHistoryFromTheDrawer()
        composeRule.onNode(hasText("Trash restore") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Trash/2021/Dedupe/IMG_001.jpg: restored").assertExists()
        composeRule.onNodeWithText("Trash/2021/VisualDupes/IMG_001.jpg: restored as IMG_001_VisualDupes.jpg")
            .assertExists()
    }
}
