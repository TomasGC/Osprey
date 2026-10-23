package app.osprey.desktop

import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsRejection
import app.osprey.settings.SettingsResult
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FolderSettingTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-folder-setting-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `the reference is the absolute normalized path and the display name is the folder name`() {
        val folder = Files.createDirectories(tempDir.resolve("Pictures"))

        val setting = folderSetting(folder.resolve("..").resolve("Pictures"))

        assertEquals(folder.toAbsolutePath().normalize().toString(), setting.reference)
        assertEquals("Pictures", setting.displayName)
    }

    @Test
    fun `a folder contains a nested one and not a sibling that starts with the same name`() {
        val pictures = folderSetting(Files.createDirectories(tempDir.resolve("Pictures")))
        val nested = folderSetting(Files.createDirectories(tempDir.resolve("Pictures").resolve("Trips")))
        val sibling = folderSetting(Files.createDirectories(tempDir.resolve("Pictures2")))

        assertTrue(pictures.location.contains(nested.location))
        assertFalse(pictures.location.contains(sibling.location))
    }

    @Test
    fun `an input inside the output is caught through real paths`() {
        val library = folderSetting(Files.createDirectories(tempDir.resolve("Library")))
        val inside = folderSetting(Files.createDirectories(tempDir.resolve("Library").resolve("2019")))
        val settings = (MediaSettings().withOutput(library) as SettingsResult.Accepted).settings

        val result = settings.addInput(inside) as SettingsResult.Rejected

        assertEquals(SettingsRejection.INPUT_INSIDE_OUTPUT, result.reason)
    }

    @Test
    fun `a filesystem root gets its own path as display name`() {
        val root = tempDir.root

        assertEquals(root.toString(), folderSetting(root).displayName)
    }
}
