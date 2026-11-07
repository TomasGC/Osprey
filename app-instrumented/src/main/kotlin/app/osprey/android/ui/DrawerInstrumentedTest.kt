package app.osprey.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.android.AndroidAppState
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.jobs.JobQueue
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SHORT_SCREEN_DP = 500

/** [content] on a screen at most [SHORT_SCREEN_DP] tall, whatever the device has. */
@Composable
private fun ShortScreen(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val capped = constraints.copy(maxHeight = minOf(constraints.maxHeight, SHORT_SCREEN_DP.dp.roundToPx()))
        val placeables = measurables.map { it.measure(capped) }
        layout(placeables.maxOf { it.width }, placeables.maxOf { it.height }) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * The drawer (#79) on a screen shorter than its content: a library and a year make it taller than a phone, and every
 * item, History included, must still be reachable. The real drawer, a real app state over the core fakes.
 */
@RunWith(AndroidJUnit4::class)
class DrawerInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settings = object : SettingsStore {
        override fun load(): MediaSettings = MediaSettings()

        override fun save(settings: MediaSettings) = Unit

        override fun hasSavedSettings(): Boolean = false
    }

    private fun showDrawer(onOpenHistory: () -> Unit = {}) {
        val fs = FakeFileSystemRepository()
        fs.addFolder(fs.root, "2021")
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val state = AndroidAppState(fs, FakeMediaMetadataReader(), settings, jobs, { null }, {})
        state.openLibrary(fs.root)
        composeRule.setContent {
            ShortScreen {
                DrawerContent(state, onOpenSettings = {}, onOpenTrash = {}, onOpenHistory = onOpenHistory, onTabSelected = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun historyCanBeScrolledToWhenTheDrawerIsShorterThanItsContent() {
        showDrawer()

        composeRule.onNodeWithText("🕘 History").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historyOpensFromTheDrawerAfterScrollingToIt() {
        var opened = 0
        showDrawer(onOpenHistory = { opened++ })

        composeRule.onNodeWithText("🕘 History").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
    }
}
