package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.AndroidAppState
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.history.RunRecorder
import app.osprey.history.openHistory
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 10_000L

/**
 * The History (#79) through the whole Android app: the real shell with its drawer, a real [AndroidAppState] over the core
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

    /** A launch of the app: a library with 2021/sub/{a,b,c}.jpg, and the history opened from its folder. */
    private fun launch(): AndroidAppState {
        val fs = FakeFileSystemRepository()
        val sub = fs.addFolder(fs.addFolder(fs.root, "2021"), "sub")
        listOf("a", "b", "c").forEach { fs.addFile(sub, "$it.jpg", "photo $it".toByteArray()) }
        val history = openHistory(historyDir)
        // As OspreyApplication does: the queue records into the same history the screens read.
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.Default), RunRecorder(history))
        return AndroidAppState(fs, FakeMediaMetadataReader(), settings, jobs, { null }, {}, history)
            .also { it.openLibrary(fs.root) }
    }

    private fun waitForJobs(state: AndroidAppState) = runBlocking {
        withTimeout(WAIT_MS) {
            state.jobs.entries.first { entries -> entries.isNotEmpty() && entries.all { it.state is JobState.Finished } }
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
    fun clearHistoryAsksFirstKeepsTheRunsOnCancelAndRemovesThemAndTheirFilesOnForget() {
        val state = launch()
        composeRule.setContent { OspreyApp(state) }
        flattenThroughTheScreen(state)
        openHistoryFromTheDrawer()

        click("Clear history")
        composeRule.onNodeWithText("Forget 2 ended runs? A run that is waiting or running stays.").assertExists()
        click("Cancel")
        assertEquals(2, historyDir.list()!!.size)
        click("Clear history")
        click("Forget")

        composeRule.onNodeWithText("No runs yet.").assertExists()
        assertEquals(0, historyDir.list()!!.size)
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
}
