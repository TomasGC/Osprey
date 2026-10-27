package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobQueue
import app.osprey.media.MediaMetadataReader
import app.osprey.settings.SettingsStore
import app.osprey.viewer.ViewTurns
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Which tab is showing, same order as the POC's nav list. */
enum class ActionTab(val label: String) {
    REORG("Reorg"),
    FLATTEN("Flatten"),
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
    val jobs: JobQueue = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO)),
) {

    /** The quarter turns given to photos in the viewer this session; cards and viewer both follow them (#51). */
    val viewTurns = ViewTurns()

    val settingsState = SettingsState(settingsStore) { openLibrary(it) }
    var libraryPath by mutableStateOf<Path?>(null)
    var years by mutableStateOf<List<String>>(emptyList())
    var selectedYear by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(ActionTab.REORG)
    var sidebarCollapsed by mutableStateOf(false)
    var hasLegacyTrash by mutableStateOf(false)

    /** Whether the Trash screen is showing instead of the selected action. */
    var showsTrash by mutableStateOf(false)

    val flatten = flattenController(fs, jobs)
    val reorg = ReorgController(ReorgPlanner(fs, mediaMetadataReader), jobs, { reorgSources() }) { refreshYears() }
    val rename = renameController(fs, jobs)
    val dedupe = dedupeController(fs, jobs, ::trashLocation)
    val trashMigration = trashMigrationController(fs, jobs) {
        refreshYears()
        refreshLegacyTrash()
    }
    val dates = datesController(fs, mediaMetadataReader, jobs)
    val standardize = standardizeController(fs, mediaMetadataReader, jobs)
    val trash = TrashController(fs, jobs) { refreshYears() }
    val visual = VisualDupesController(fs, mediaMetadataReader, jobs, ::trashLocation)

    /**
     * Loads the saved settings, which open their output folder as the library. It reads state it has just
     * written, and a scope that composed it would be subscribed to that state: when a job ended and wrote
     * [years], the scope would recompose (#56). Nothing observes these reads.
     */
    fun start() = Snapshot.withoutReadObservation { settingsState.start() }

    fun openLibrary(path: Path) {
        libraryPath = path
        years = listYears(path)
        selectedYear = years.lastOrNull()
        refreshLegacyTrash()
    }

    /** Opens the Trash screen, freshly read, or goes back to the actions when it is already open. */
    fun toggleTrash() {
        val root = libraryHandle()
        if (showsTrash || root == null) {
            showsTrash = false
            return
        }
        settingsState.showing = false
        trash.refresh(root)
        showsTrash = true
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

    /** The library root itself, for the actions that run on the whole library; null until a library is chosen. */
    fun libraryHandle(): FileHandle? = libraryPath?.let { PathHandle(it) }

    /** The saved input folders as Reorg sources; one that is gone or not a folder has no handle (unreachable). */
    private fun reorgSources(): List<ReorgSource> = settingsState.settings.inputs.map { input ->
        val path = Path.of(input.folder.reference)
        ReorgSource(if (Files.isDirectory(path)) PathHandle(path) else null, input.folder.displayName, input.mode)
    }

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
