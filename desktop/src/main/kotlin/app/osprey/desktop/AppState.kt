package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaMetadataReader
import app.osprey.settings.SettingsStore
import java.nio.file.Files
import java.nio.file.Path

/** Which tab is showing, same order as the POC's nav list. */
enum class ActionTab(val label: String) {
    FLATTEN("Flatten"),
    YEAR_FOLDERS("Year folders"),
    RENAME("Rename"),
    DEDUPE("Dedupe"),
    VISUAL("Visual Dupes"),
    DATES("Dates"),
    STANDARDIZE("Standardize"),
    TRASH_MIGRATION("Migrate old trash"),
}

/** Top-level app state: the sidebar's library/year selection, plus one controller per tab. */
class AppState(
    val fs: FileSystemRepository,
    val mediaMetadataReader: MediaMetadataReader,
    private val settingsStore: SettingsStore,
) {

    val settingsState = SettingsState(settingsStore) { openLibrary(it) }
    var libraryPath by mutableStateOf<Path?>(null)
    var years by mutableStateOf<List<String>>(emptyList())
    var selectedYear by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(ActionTab.FLATTEN)
    var sidebarCollapsed by mutableStateOf(false)
    var hasLegacyTrash by mutableStateOf(false)

    val flatten = flattenController(fs)
    val yearFolders = yearFoldersController(fs, mediaMetadataReader)
    val rename = renameController(fs)
    val dedupe = dedupeController(fs, ::trashLocation)
    val trashMigration = trashMigrationController(fs)
    val dates = datesController(fs, mediaMetadataReader)
    val standardize = standardizeController(fs, mediaMetadataReader)

    /** Loads the saved settings, which open their output folder as the library. */
    fun start() = settingsState.start()

    fun openLibrary(path: Path) {
        libraryPath = path
        years = listYears(path)
        selectedYear = years.lastOrNull()
        refreshLegacyTrash()
    }

    /** The tabs to show: "Migrate old trash" only while an old `TODELETE*` folder is left. */
    val visibleTabs: List<ActionTab>
        get() = ActionTab.entries.filter { it != ActionTab.TRASH_MIGRATION || hasLegacyTrash }

    /** Where the selected year's trash lives; only called once a library and a year are chosen. */
    fun trashLocation(): TrashLocation =
        TrashLocation(checkNotNull(libraryHandle()), checkNotNull(selectedYear))

    /** Re-checks the library for old `TODELETE*` folders. */
    fun refreshLegacyTrash() {
        val root = libraryHandle()
        hasLegacyTrash = root != null && TrashMigrationPlanner(fs).hasLegacy(root)
    }

    /** The library root itself, for the action that creates the year folders; null until a library is chosen. */
    fun libraryHandle(): FileHandle? = libraryPath?.let { PathHandle(it) }

    /** Re-reads the year folders on disk, keeping the selection when its folder still exists. */
    fun refreshYears() {
        val library = libraryPath ?: return
        years = listYears(library)
        if (selectedYear !in years) selectedYear = years.lastOrNull()
    }

    /** The currently selected year folder, or null until a library and a year are both chosen. */
    fun yearHandle(): FileHandle? {
        val library = libraryPath ?: return null
        val year = selectedYear ?: return null
        return PathHandle(library.resolve(year))
    }
}

private fun listYears(libraryPath: Path): List<String> {
    if (!Files.isDirectory(libraryPath)) return emptyList()
    return Files.newDirectoryStream(libraryPath).use { stream ->
        stream
            .filter { Files.isDirectory(it) && isAllDigits(it.fileName.toString()) }
            .map { it.fileName.toString() }
            .sorted()
    }
}

private fun isAllDigits(name: String): Boolean = name.isNotEmpty() && name.all(Char::isDigit)
