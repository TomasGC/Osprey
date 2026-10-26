package app.osprey.android

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {

    private class CountingStore : SettingsStore {
        var loads = 0

        override fun load(): MediaSettings {
            loads++
            return MediaSettings()
        }

        override fun save(settings: MediaSettings) = Unit

        override fun hasSavedSettings(): Boolean = false
    }

    private val fs = FakeFileSystemRepository()
    private val store = CountingStore()
    private val state = AndroidAppState(fs, FakeMediaMetadataReader(), store, immediateJobQueue(), { null }, {})

    @Test
    fun `starting twice loads the saved settings once`() {
        state.start()
        state.start()

        assertEquals(1, store.loads)
    }

    @Test
    fun `openLibrary lists only numeric-named subfolders, sorted, and selects the latest`() {
        fs.addFolder(fs.root, "2023")
        fs.addFolder(fs.root, "2021")
        fs.addFolder(fs.root, "2022")
        fs.addFolder(fs.root, "Trash")
        fs.addFile(fs.root, "2024")

        state.openLibrary(fs.root)

        assertEquals(listOf("2021", "2022", "2023"), state.years)
        assertEquals("2023", state.selectedYear)
        assertTrue(state.yearHandle() != null)
    }

    @Test
    fun `yearHandle is null until both a library and a year are selected`() {
        assertNull(state.yearHandle())
        state.openLibrary(fs.root)
        assertNull(state.yearHandle())
    }

    @Test
    fun `refreshing the years keeps the selection while its folder exists and moves it when it is gone`() {
        fs.addFolder(fs.root, "2021")
        val latest = fs.addFolder(fs.root, "2022")
        state.openLibrary(fs.root)
        state.selectedYear = "2021"

        state.refreshYears()
        assertEquals("2021", state.selectedYear)
        fs.delete(fs.list(fs.root).single { it.name == "2021" }.handle)
        state.refreshYears()

        assertEquals("2022", state.selectedYear)
        assertEquals(latest, state.yearHandle())
    }

    @Test
    fun `refreshing the years without a library does nothing`() {
        state.refreshYears()

        assertTrue(state.years.isEmpty())
    }

    @Test
    fun `the migration tab shows only while an old trash folder is left`() {
        fs.addFolder(fs.addFolder(fs.root, "2019"), "TODELETE")
        state.openLibrary(fs.root)
        assertTrue(ActionTab.TRASH_MIGRATION in state.visibleTabs)

        fs.delete(fs.list(fs.list(fs.root).single { it.name == "2019" }.handle).single().handle)
        state.refreshLegacyTrash()

        assertFalse(ActionTab.TRASH_MIGRATION in state.visibleTabs)
    }

    @Test
    fun `the Trash screen opens on a fresh read and closes on the second tap`() {
        state.openLibrary(fs.root)

        state.toggleTrash()
        assertTrue(state.showsTrash)
        state.toggleTrash()

        assertFalse(state.showsTrash)
    }

    @Test
    fun `the Trash screen does not open before a library is chosen`() {
        state.toggleTrash()

        assertFalse(state.showsTrash)
    }

    @Test
    fun `settings show until a library is chosen`() {
        assertTrue(state.showsSettings)
        state.openLibrary(fs.root)
        assertFalse(state.showsSettings)
    }

    @Test
    fun `the trash location is the library and the selected year`() {
        fs.addFolder(fs.root, "2019")
        state.openLibrary(fs.root)

        assertEquals("2019", state.trashLocation().year)
    }
}
