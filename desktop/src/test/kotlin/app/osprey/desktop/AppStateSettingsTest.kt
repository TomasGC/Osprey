package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsResult
import app.osprey.settings.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSettingsStore(var stored: MediaSettings = MediaSettings(), var saved: Boolean = false) : SettingsStore {
    override fun load(): MediaSettings = stored

    override fun save(settings: MediaSettings) {
        stored = settings
        saved = true
    }

    override fun hasSavedSettings(): Boolean = saved
}

class AppStateSettingsTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-appstate-settings-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun dir(name: String): Path = Files.createDirectories(tempDir.resolve(name))

    private fun stateWith(store: FakeSettingsStore) =
        AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), store)

    private fun settingsOf(output: Path): MediaSettings =
        (MediaSettings().withOutput(folderSetting(output)) as SettingsResult.Accepted).settings

    @Test
    fun `start with nothing saved shows Settings and opens no library`() {
        val state = stateWith(FakeSettingsStore())

        state.start()

        assertTrue(state.settingsState.showing)
        assertNull(state.libraryPath)
    }

    @Test
    fun `start with saved settings opens the output and hides Settings`() {
        Files.createDirectory(dir("library").resolve("2021"))
        val state = stateWith(FakeSettingsStore(settingsOf(tempDir.resolve("library")), saved = true))

        state.start()

        assertFalse(state.settingsState.showing)
        assertEquals(tempDir.resolve("library").toAbsolutePath().normalize(), state.libraryPath)
        assertEquals(listOf("2021"), state.years)
    }

    @Test
    fun `start with a library from before Settings opens it but shows Settings once`() {
        val state = stateWith(FakeSettingsStore(settingsOf(dir("library")), saved = false))

        state.start()

        assertTrue(state.settingsState.showing)
        assertNotNull(state.libraryPath)
    }

    @Test
    fun `closing Settings saves them and hides the screen`() {
        val store = FakeSettingsStore(settingsOf(dir("library")), saved = false)
        val state = stateWith(store)
        state.start()

        state.settingsState.close()

        assertFalse(state.settingsState.showing)
        assertTrue(store.saved)
    }

    @Test
    fun `choosing an output saves it, opens it as the library and lists its years`() {
        Files.createDirectory(dir("library").resolve("2020"))
        val store = FakeSettingsStore()
        val state = stateWith(store)
        state.start()

        state.settingsState.chooseOutput(tempDir.resolve("library"))

        assertEquals(tempDir.resolve("library").toAbsolutePath().normalize().toString(), store.stored.output?.reference)
        assertEquals(listOf("2020"), state.years)
        assertNull(state.settingsState.message)
    }

    @Test
    fun `an output overlapping an input is refused with a message and changes nothing`() {
        val store = FakeSettingsStore()
        val state = stateWith(store)
        state.start()
        state.settingsState.chooseOutput(dir("library"))
        state.settingsState.addInput(dir("camera"))

        state.settingsState.chooseOutput(tempDir.resolve("camera"))

        assertTrue(state.settingsState.message.orEmpty().contains("camera"))
        assertEquals(tempDir.resolve("library").toAbsolutePath().normalize().toString(), store.stored.output?.reference)
    }

    @Test
    fun `adding an input saves it as Copy`() {
        val store = FakeSettingsStore()
        val state = stateWith(store)
        state.start()
        state.settingsState.chooseOutput(dir("library"))

        state.settingsState.addInput(dir("camera"))

        assertEquals(listOf(InputMode.COPY), store.stored.inputs.map { it.mode })
        assertEquals(store.stored, state.settingsState.settings)
    }

    @Test
    fun `an input inside the output is refused with a message and not saved`() {
        val store = FakeSettingsStore()
        val state = stateWith(store)
        state.start()
        state.settingsState.chooseOutput(dir("library"))

        state.settingsState.addInput(dir("library/Trips"))

        assertTrue(state.settingsState.message.orEmpty().isNotBlank())
        assertTrue(store.stored.inputs.isEmpty())
    }

    @Test
    fun `a later successful change clears the previous message`() {
        val state = stateWith(FakeSettingsStore())
        state.start()
        state.settingsState.chooseOutput(dir("library"))
        state.settingsState.addInput(dir("library/Trips"))

        state.settingsState.addInput(dir("camera"))

        assertNull(state.settingsState.message)
    }

    @Test
    fun `switching an input to Move and removing another are saved`() {
        val store = FakeSettingsStore()
        val state = stateWith(store)
        state.start()
        state.settingsState.chooseOutput(dir("library"))
        state.settingsState.addInput(dir("camera"))
        state.settingsState.addInput(dir("whatsapp"))
        val camera = folderSetting(tempDir.resolve("camera")).reference
        val whatsapp = folderSetting(tempDir.resolve("whatsapp")).reference

        state.settingsState.setInputMode(whatsapp, InputMode.MOVE)
        state.settingsState.removeInput(camera)

        assertEquals(listOf(whatsapp to InputMode.MOVE), store.stored.inputs.map { it.folder.reference to it.mode })
    }
}
