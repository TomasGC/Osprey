package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobQueue
import app.osprey.media.MediaMetadataReader
import app.osprey.settings.FolderSetting
import app.osprey.settings.SettingsStore
import app.osprey.viewer.TurnSaver
import app.osprey.viewer.ViewTurns

/** Which screen is showing, same actions and order as desktop's `ActionTab` (#11). */
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

/** Top-level app state: the chosen library root, year selection, plus one controller per tab. */
class AndroidAppState(
    val fs: FileSystemRepository,
    val mediaMetadataReader: MediaMetadataReader,
    settingsStore: SettingsStore,
    val jobs: JobQueue,
    private val resolveFolder: (FolderSetting) -> FileHandle?,
    releaseGrant: (String) -> Unit,
) {

    /**
     * The quarter turns given to photos in the viewer this session; cards and viewer both follow them (#51), and the
     * viewer can write a turn into a JPEG's file (#61).
     */
    val viewTurns = ViewTurns(TurnSaver(fs)::save)

    val settingsState = SettingsState(
        settingsStore,
        onOutputChosen = { folder -> resolveFolder(folder)?.let { openLibrary(it) } },
        releaseGrant = releaseGrant,
    )

    var libraryRoot by mutableStateOf<FileHandle?>(null)
    var years by mutableStateOf<List<String>>(emptyList())
    var selectedYear by mutableStateOf<String?>(null)
    var selectedTab by mutableStateOf(ActionTab.REORG)
    var hasLegacyTrash by mutableStateOf(false)

    /** Whether the Trash screen is showing instead of the selected action. */
    var showsTrash by mutableStateOf(false)

    /** True while the Settings screen is up, which is also the only screen until an output folder is set. */
    val showsSettings: Boolean get() = settingsState.showing || libraryRoot == null

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

    private var started = false

    /**
     * Loads the saved settings, which open their output folder as the library. This state outlives the activity
     * (a run in progress must), so a second activity over the same state leaves what the first one set up alone.
     */
    fun start() {
        if (started) return
        started = true
        settingsState.start()
    }

    fun openLibrary(root: FileHandle) {
        libraryRoot = root
        years = listYears(fs, root)
        selectedYear = years.lastOrNull()
        refreshLegacyTrash()
    }

    /** Opens the Trash screen, freshly read, or goes back to the actions when it is already open. */
    fun toggleTrash() {
        val root = libraryRoot
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
        TrashLocation(checkNotNull(libraryRoot), checkNotNull(selectedYear))

    /** Re-checks the library for old `TODELETE*` folders. */
    fun refreshLegacyTrash() {
        val root = libraryRoot
        hasLegacyTrash = root != null && TrashMigrationPlanner(fs).hasLegacy(root)
    }

    /** The saved input folders as Reorg sources; one whose grant no longer resolves has no handle (unreachable). */
    private fun reorgSources(): List<ReorgSource> = settingsState.settings.inputs.map { input ->
        ReorgSource(resolveFolder(input.folder), input.folder.displayName, input.mode)
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
