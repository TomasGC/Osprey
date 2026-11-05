package app.osprey.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The sidebar's History and Settings: the word beside the icon opens the screen just like the icon does (#79). */
@OptIn(ExperimentalTestApi::class)
class SidebarTest : TimedTest() {

    private lateinit var library: Path

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-sidebar-test")
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun state() = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore())
        .also { it.openLibrary(library) }

    @Test
    fun `the History label opens the History and a second click closes it`() = runComposeUiTest {
        val state = state()
        setContent { Sidebar(state) }

        onNodeWithText("History").performClick()
        waitForIdle()
        assertTrue(state.showsHistory)
        onNodeWithText("History").performClick()
        waitForIdle()

        assertFalse(state.showsHistory)
    }

    @Test
    fun `the History icon opens the History and a second click closes it`() = runComposeUiTest {
        val state = state()
        setContent { Sidebar(state) }

        onNodeWithText("🕘").performClick()
        waitForIdle()
        assertTrue(state.showsHistory)
        onNodeWithText("🕘").performClick()
        waitForIdle()

        assertFalse(state.showsHistory)
    }

    @Test
    fun `the Settings icon opens the Settings and a second click closes it`() = runComposeUiTest {
        val state = state()
        setContent { Sidebar(state) }

        onNodeWithText("⚙").performClick()
        waitForIdle()
        assertTrue(state.settingsState.showing)
        onNodeWithText("⚙").performClick()
        waitForIdle()

        assertFalse(state.settingsState.showing)
    }

    @Test
    fun `the Settings label opens the Settings, closes the other screens and a second click closes it`() =
        runComposeUiTest {
            val state = state()
            state.toggleHistory()
            setContent { Sidebar(state) }

            onNodeWithText("Settings").performClick()
            waitForIdle()
            assertTrue(state.settingsState.showing)
            assertFalse(state.showsHistory)
            onNodeWithText("Settings").performClick()
            waitForIdle()

            assertFalse(state.settingsState.showing)
        }
}
