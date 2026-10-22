package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaMetadataReader

/** Which screen is showing, same actions and order as desktop's `ActionTab` (#11). */
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

/** Top-level app state: the chosen library root, year selection, plus one controller per tab. */
class AndroidAppState(val fs: FileSystemRepository, val mediaMetadataReader: MediaMetadataReader) {

    var libraryRoot by mutableStateOf<FileHandle?>(null)
    var years by mutableStateOf<List<String>>(emptyList())
    var selectedYear by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(ActionTab.FLATTEN)
    var hasLegacyTrash by mutableStateOf(false)

    val flatten = flattenController(fs)
    val yearFolders = yearFoldersController(fs, mediaMetadataReader)
    val rename = renameController(fs)
    val dedupe = dedupeController(fs, ::trashLocation)
    val trashMigration = trashMigrationController(fs)
    val dates = datesController(fs, mediaMetadataReader)
    val standardize = standardizeController(fs, mediaMetadataReader)

    fun openLibrary(root: FileHandle) {
        libraryRoot = root
        years = listYears(fs, root)
        selectedYear = years.lastOrNull()
        refreshLegacyTrash()
    }

    /** The tabs to show: "Migrate old trash" only while an old `TODELETE*` folder is left. */
    val visibleTabs: List<ActionTab>
        get() = ActionTab.entries.filter { it != ActionTab.TRASH_MIGRATION || hasLegacyTrash }

    /** Where the selected year's trash lives; only called once a library and a year are chosen. */
    fun trashLocation(): TrashLocation =
        TrashLocation(checkNotNull(libraryRoot), checkNotNull(selectedYear))

    /** Re-checks the library for old `TODELETE*` folders. */
    fun refreshLegacyTrash() {
        val root = libraryRoot
        hasLegacyTrash = root != null && TrashMigrationPlanner(fs).hasLegacy(root)
    }

    /** Re-reads the year folders on disk, keeping the selection when its folder still exists. */
    fun refreshYears() {
        val root = libraryRoot ?: return
        years = listYears(fs, root)
        if (selectedYear !in years) selectedYear = years.lastOrNull()
    }

    /** The currently selected year folder, or null until a library and a year are both chosen. */
    fun yearHandle(): FileHandle? {
        val root = libraryRoot ?: return null
        val year = selectedYear ?: return null
        return fs.list(root).find { it.isDirectory && it.name == year }?.handle
    }
}

private fun listYears(fs: FileSystemRepository, root: FileHandle): List<String> =
    fs.list(root)
        .filter { it.isDirectory && isAllDigits(it.name) }
        .map { it.name }
        .sorted()

private fun isAllDigits(name: String): Boolean = name.isNotEmpty() && name.all(Char::isDigit)
