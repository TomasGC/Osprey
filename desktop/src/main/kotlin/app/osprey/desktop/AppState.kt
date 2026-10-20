package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaMetadataReader
import java.nio.file.Files
import java.nio.file.Path

/** Which simple-action tab is showing. Visual Dupes is out of scope here (#12). */
enum class ActionTab(val label: String) {
    FLATTEN("Flatten"),
    RENAME("Rename"),
    DEDUPE("Dedupe"),
    DATES("Dates"),
    STANDARDIZE("Standardize"),
}

/** Top-level app state: the sidebar's library/year selection, plus one controller per tab. */
class AppState(fs: FileSystemRepository, mediaMetadataReader: MediaMetadataReader) {

    var libraryPath by mutableStateOf<Path?>(null)
    var years by mutableStateOf<List<String>>(emptyList())
    var selectedYear by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(ActionTab.FLATTEN)
    var sidebarCollapsed by mutableStateOf(false)

    val flatten = flattenController(fs)
    val rename = renameController(fs)
    val dedupe = dedupeController(fs)
    val dates = datesController(fs, mediaMetadataReader)
    val standardize = standardizeController(fs, mediaMetadataReader)

    fun openLibrary(path: Path) {
        libraryPath = path
        OspreyConfig.saveLibraryPath(path)
        years = listYears(path)
        selectedYear = years.lastOrNull()
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
