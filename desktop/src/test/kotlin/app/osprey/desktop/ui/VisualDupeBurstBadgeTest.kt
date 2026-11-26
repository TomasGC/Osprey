package app.osprey.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.desktop.AppState
import app.osprey.desktop.FakeSettingsStore
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.toUiGroups
import java.nio.file.Files
import org.junit.Test

/** The card of a burst whose names another tool lowercased says it is a burst, and how many frames stay (#141). */
@OptIn(ExperimentalTestApi::class)
class VisualDupeBurstBadgeTest : TimedTest() {

    @Test
    fun `a lowercase burst set shows its badge with the number of frames kept`() = runComposeUiTest {
        val year = Files.createTempDirectory("osprey-burst-badge")
        try {
            val names = listOf("img_0000_burst1.jpg", "img_0001_burst1.jpg", "other.jpg")
            val members = names.map { VisualDupeMember(PathHandle(year.resolve(it)), it, 1) }
            val state = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore())
            val group = toUiGroups(listOf(members)).single()

            setContent { VisualDupeGroupCard(state, PathHandle(year), group) {} }

            onNodeWithText("BURST SET — KEEP ALL 2").assertIsDisplayed()
        } finally {
            year.toFile().deleteRecursively()
        }
    }
}
