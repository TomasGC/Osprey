package app.osprey.desktop

import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsResult
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopSettingsStoreTest {

    private lateinit var tempDir: Path
    private val store = DesktopSettingsStore()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-settings-test")
        System.setProperty("osprey.config.path", tempDir.resolve("config.json").toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("osprey.config.path")
        tempDir.toFile().deleteRecursively()
    }

    private fun dir(name: String): Path = Files.createDirectories(tempDir.resolve(name))

    private fun configFile() = tempDir.resolve("config.json").toFile()

    private fun settingsOf(output: Path, vararg inputs: Pair<Path, InputMode>): MediaSettings {
        var settings = (MediaSettings().withOutput(folderSetting(output)) as SettingsResult.Accepted).settings
        for ((path, mode) in inputs) {
            settings = (settings.addInput(folderSetting(path)) as SettingsResult.Accepted).settings
                .withMode(folderSetting(path).reference, mode)
        }
        return settings
    }

    @Test
    fun `load returns empty settings when the config file is missing`() {
        assertEquals(MediaSettings(), store.load())
    }

    @Test
    fun `load returns empty settings when the config file is malformed`() {
        configFile().writeText("not valid json")

        assertEquals(MediaSettings(), store.load())
    }

    @Test
    fun `save then load round-trips the output, the inputs, their order and their modes`() {
        val settings = settingsOf(dir("library"), dir("camera") to InputMode.COPY, dir("whatsapp") to InputMode.MOVE)

        store.save(settings)

        val loaded = store.load()
        assertEquals(settings.output?.reference, loaded.output?.reference)
        assertEquals(settings.inputs.map { it.folder.reference }, loaded.inputs.map { it.folder.reference })
        assertEquals(listOf(InputMode.COPY, InputMode.MOVE), loaded.inputs.map { it.mode })
    }

    @Test
    fun `save preserves other keys already in the config file`() {
        configFile().writeText("""{"other_setting": "keep me"}""")

        store.save(settingsOf(dir("library")))

        assertEquals("keep me", JSONObject(configFile().readText()).getString("other_setting"))
    }

    @Test
    fun `a library saved before settings existed is read as the output`() {
        val library = dir("library")
        OspreyConfig.saveLibraryPath(library)
        val json = JSONObject(configFile().readText())
        json.remove("output_path")
        configFile().writeText(json.toString())

        val loaded = store.load()

        assertEquals(library.toAbsolutePath().normalize().toString(), loaded.output?.reference)
        assertTrue(loaded.inputs.isEmpty())
    }

    @Test
    fun `the saved output wins over the older library key`() {
        val old = dir("old")
        val current = dir("current")
        OspreyConfig.saveLibraryPath(old)
        store.save(settingsOf(current))
        val json = JSONObject(configFile().readText())
        json.put("library_path", old.toString())
        configFile().writeText(json.toString())

        assertEquals(current.toAbsolutePath().normalize().toString(), store.load().output?.reference)
    }

    @Test
    fun `saving settings without an output forgets the saved library`() {
        store.save(settingsOf(dir("library")))

        store.save(MediaSettings())

        assertNull(store.load().output)
        assertNull(OspreyConfig.loadLibraryPath())
    }

    @Test
    fun `the library path from the command line becomes the output and keeps the inputs`() {
        store.save(settingsOf(dir("library"), dir("camera") to InputMode.MOVE))
        val other = dir("other")

        OspreyConfig.saveLibraryPath(other)

        val loaded = store.load()
        assertEquals(other.toAbsolutePath().normalize().toString(), loaded.output?.reference)
        assertEquals(listOf(InputMode.MOVE), loaded.inputs.map { it.mode })
    }

    @Test
    fun `an input entry with an unknown mode loads as Copy and one without a path is skipped`() {
        store.save(settingsOf(dir("library")))
        val json = JSONObject(configFile().readText())
        val camera = dir("camera").toAbsolutePath().normalize().toString()
        json.put(
            "input_folders",
            org.json.JSONArray()
                .put(JSONObject().put("path", camera).put("mode", "TELEPORT"))
                .put(JSONObject().put("mode", "MOVE")),
        )
        configFile().writeText(json.toString())

        val loaded = store.load()

        assertEquals(listOf(camera), loaded.inputs.map { it.folder.reference })
        assertEquals(listOf(InputMode.COPY), loaded.inputs.map { it.mode })
    }

    @Test
    fun `settings count as saved only once they were saved, not for a legacy library alone`() {
        assertTrue(!store.hasSavedSettings())
        OspreyConfig.saveLibraryPath(dir("library"))
        val json = JSONObject(configFile().readText())
        json.remove("output_path")
        configFile().writeText(json.toString())
        assertTrue(!store.hasSavedSettings())

        store.save(store.load())

        assertTrue(store.hasSavedSettings())
    }
}
