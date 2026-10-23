package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppStateTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-appstate-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun newState() = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), FakeSettingsStore())

    @Test
    fun `openLibrary lists only numeric-named subfolders, sorted, as years`() {
        Files.createDirectory(tempDir.resolve("2023"))
        Files.createDirectory(tempDir.resolve("2021"))
        Files.createDirectory(tempDir.resolve("2022"))
        Files.createDirectory(tempDir.resolve("TODELETE"))
        Files.writeString(tempDir.resolve("notes.txt"), "x")

        val state = newState()
        state.openLibrary(tempDir)

        assertEquals(listOf("2021", "2022", "2023"), state.years)
    }

    @Test
    fun `openLibrary selects the most recent year by default`() {
        Files.createDirectory(tempDir.resolve("2021"))
        Files.createDirectory(tempDir.resolve("2023"))

        val state = newState()
        state.openLibrary(tempDir)

        assertEquals("2023", state.selectedYear)
    }

    @Test
    fun `yearHandle is null until both a library and a year are selected`() {
        assertNull(newState().yearHandle())
    }
}
