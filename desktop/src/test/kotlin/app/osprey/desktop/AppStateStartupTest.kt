package app.osprey.desktop

import androidx.compose.runtime.snapshots.Snapshot
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsResult
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Where the state is built decides what recomposes (#56): a Compose state read while composing subscribes the
 * composing scope to it, so a state built and started inside `application { }` made that scope recompose, and
 * build a second state, when a job ended and wrote `years`.
 */
class AppStateStartupTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-appstate-startup-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun libraryWithYears(): Path {
        val library = Files.createDirectories(tempDir.resolve("library"))
        Files.createDirectories(library.resolve("2023"))
        Files.createDirectories(library.resolve("2024"))
        return library
    }

    private fun readsDuring(block: () -> Unit): List<Any> {
        val reads = mutableListOf<Any>()
        Snapshot.observe(readObserver = { reads += it }, block = block)
        return reads
    }

    @Test
    fun `building and starting a state with a saved library reads no Compose state`() {
        val output = (MediaSettings().withOutput(folderSetting(libraryWithYears())) as SettingsResult.Accepted)
        val store = FakeSettingsStore(output.settings, saved = true)

        val reads = readsDuring {
            AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), store).start()
        }

        assertEquals(emptyList<Any>(), reads)
    }

    @Test
    fun `building and starting a state with nothing saved reads no Compose state`() {
        val reads = readsDuring {
            AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore()).start()
        }

        assertEquals(emptyList<Any>(), reads)
    }
}
