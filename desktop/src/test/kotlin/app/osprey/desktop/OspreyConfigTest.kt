package app.osprey.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OspreyConfigTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-config-test")
        System.setProperty("osprey.config.path", tempDir.resolve("config.json").toString())
    }

    @After
    fun tearDown() {
        System.clearProperty("osprey.config.path")
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `loadLibraryPath returns null when the config file is missing`() {
        assertNull(OspreyConfig.loadLibraryPath())
    }

    @Test
    fun `loadLibraryPath returns null when the config file is malformed`() {
        val configFile = tempDir.resolve("config.json").toFile()
        configFile.parentFile.mkdirs()
        configFile.writeText("not valid json")

        assertNull(OspreyConfig.loadLibraryPath())
    }

    @Test
    fun `saveLibraryPath then loadLibraryPath round-trips`() {
        val lib = Files.createDirectory(tempDir.resolve("library"))

        OspreyConfig.saveLibraryPath(lib)

        assertEquals(lib.toAbsolutePath().normalize(), OspreyConfig.loadLibraryPath())
    }

    @Test
    fun `saveLibraryPath preserves other keys already in the config file`() {
        val configFile = tempDir.resolve("config.json").toFile()
        configFile.parentFile.mkdirs()
        configFile.writeText("""{"other_setting": "keep me"}""")
        val lib = Files.createDirectory(tempDir.resolve("library"))

        OspreyConfig.saveLibraryPath(lib)

        val saved = org.json.JSONObject(configFile.readText())
        assertEquals("keep me", saved.getString("other_setting"))
        assertEquals(lib.toAbsolutePath().normalize().toString(), saved.getString("library_path"))
    }

    @Test
    fun `resolveLibraryPath with a CLI arg wins and is saved for next time`() {
        val oldLib = Files.createDirectory(tempDir.resolve("old"))
        OspreyConfig.saveLibraryPath(oldLib)
        val newLib = Files.createDirectory(tempDir.resolve("new"))

        val result = OspreyConfig.resolveLibraryPath(newLib.toString())

        assertEquals(newLib.toAbsolutePath().normalize(), result)
        assertEquals(newLib.toAbsolutePath().normalize(), OspreyConfig.loadLibraryPath())
    }

    @Test
    fun `resolveLibraryPath with no CLI arg falls back to the saved config`() {
        val lib = Files.createDirectory(tempDir.resolve("library"))
        OspreyConfig.saveLibraryPath(lib)

        assertEquals(lib.toAbsolutePath().normalize(), OspreyConfig.resolveLibraryPath(null))
    }

    @Test
    fun `resolveLibraryPath returns null when nothing is configured`() {
        assertNull(OspreyConfig.resolveLibraryPath(null))
    }

    @Test
    fun `resolveLibraryPath with a blank CLI arg falls back to the saved config`() {
        val lib = Files.createDirectory(tempDir.resolve("library"))
        OspreyConfig.saveLibraryPath(lib)

        assertEquals(lib.toAbsolutePath().normalize(), OspreyConfig.resolveLibraryPath("  "))
    }
}
