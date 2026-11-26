package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.android.AndroidAppState
import app.osprey.android.toUiGroups
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.jobs.JobQueue
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Android card of a burst whose names another tool lowercased says so, over the core fakes (#141). */
@RunWith(AndroidJUnit4::class)
class VisualDupeBurstBadgeInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settings = object : SettingsStore {
        override fun load(): MediaSettings = MediaSettings()

        override fun save(settings: MediaSettings) = Unit

        override fun hasSavedSettings(): Boolean = false
    }

    @Test
    fun aLowercaseBurstSetShowsItsBadgeWithTheNumberOfFramesKept() {
        val fs = FakeFileSystemRepository()
        val names = listOf("img_0000_burst1.jpg", "img_0001_burst1.jpg", "other.jpg")
        val members = names.map { VisualDupeMember(fs.addFile(fs.root, it), it, 1) }
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val state = AndroidAppState(fs, FakeMediaMetadataReader(), settings, jobs, { null }, {})
        val group = toUiGroups(listOf(members)).single()

        composeRule.setContent { VisualDupeGroupCard(state, fs.root, group) {} }

        composeRule.onNodeWithText("BURST SET — KEEP ALL 2").assertIsDisplayed()
    }
}
