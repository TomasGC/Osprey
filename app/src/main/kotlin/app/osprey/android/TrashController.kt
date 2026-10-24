package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.RestoreConflict
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashClearer
import app.osprey.actions.trash.TrashContents
import app.osprey.actions.trash.TrashEntry
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.actions.trash.TrashSelection
import app.osprey.actions.trash.clearConfirmation
import app.osprey.actions.trash.clearResultLines
import app.osprey.actions.trash.restoreResultLines
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobControl
import app.osprey.jobs.JobProgress
import app.osprey.jobs.progressText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Trash screen (#41): what the Trash holds by year and action, the files ticked across its tabs, and
 * the two things that can be done with them. Delete is the only irreversible step in Osprey, so it
 * waits for [confirmation] to be accepted; Restore moves files back and hands the name clashes to
 * [conflicts] for the user to settle. Both report progress per file and can be stopped between two.
 */
class TrashController(private val fs: FileSystemRepository) {

    val state = ActionUiState()

    var contents by mutableStateOf(TrashContents(emptyList()))
        private set
    var selection by mutableStateOf(TrashSelection())
        private set
    var year by mutableStateOf<String?>(null)
        private set

    /** The open action tab; a tab is chosen by setting it, [selectYear] also picks the year's first action. */
    var action by mutableStateOf<TrashAction?>(null)

    /** The question to answer before deleting, or null when none is pending; set it to null to dismiss. */
    var confirmation by mutableStateOf<String?>(null)

    /** Restored files whose name is taken in the library, waiting for a choice. */
    var conflicts by mutableStateOf(emptyList<RestoreConflict>())
        private set
    var report by mutableStateOf(emptyList<String>())
        private set

    @Volatile
    private var stopRequested = false

    /** The files of the open year and action tab. */
    val currentTab: List<TrashEntry>
        get() {
            val openYear = year ?: return emptyList()
            val openAction = action ?: return emptyList()
            return contents.entries(openYear, openAction)
        }

    val tabFullySelected: Boolean get() = selection.allSelected(currentTab)

    /** Re-reads the Trash of [root]; the selection keeps what is still there and the open tab stays if it can. */
    fun refresh(root: FileHandle) {
        contents = TrashScanner(fs).scan(root)
        selection = selection.retainedIn(contents)
        year = year?.takeIf { it in contents.years } ?: contents.years.firstOrNull()
        val openYear = year
        val actions = if (openYear == null) emptyList() else contents.actions(openYear)
        action = action?.takeIf { it in actions } ?: actions.firstOrNull()
    }

    fun selectYear(newYear: String) {
        year = newYear
        action = contents.actions(newYear).firstOrNull()
    }

    fun toggle(entry: TrashEntry) {
        selection = selection.toggle(entry)
    }

    /** Selects every file of the open tab, or unselects them when they all were. */
    fun toggleTab() {
        selection = selection.toggleAll(currentTab)
    }

    /** Asks before deleting the selection; with nothing selected there is nothing to ask. */
    fun requestDelete() {
        val selected = selection.entriesIn(contents)
        confirmation = if (selected.isEmpty()) null else clearConfirmation(selected)
    }

    suspend fun confirmDelete(root: FileHandle) {
        confirmation = null
        val selected = selection.entriesIn(contents)
        if (selected.isEmpty()) return
        stopRequested = false
        val result = runWithProgress(state, { stopRequested }) { control ->
            TrashClearer(fs).clear(root, selected, control)
        }
        report = clearResultLines(result)
        refresh(root)
    }

    suspend fun restore(root: FileHandle) {
        val selected = selection.entriesIn(contents)
        if (selected.isEmpty()) return
        val restorer = TrashRestorer(fs)
        val plan = restorer.plan(root, selected)
        stopRequested = false
        val result = runWithProgress(state, { stopRequested }) { control ->
            restorer.restore(root, plan, control)
        }
        conflicts = plan.conflicts
        report = restoreResultLines(result, plan.conflicts.size)
        refresh(root)
    }

    /** Settles [conflict] as [choice], then forgets it. A failure is added to the report. */
    fun resolve(root: FileHandle, conflict: RestoreConflict, choice: ConflictChoice) {
        val failure = TrashRestorer(fs).resolve(root, conflict, choice)
        if (failure != null) report = report + "  FAILED ${failure.label}: ${failure.reason} (still in the Trash)"
        conflicts = conflicts - conflict
        refresh(root)
    }

    /** Settles every waiting conflict the same way. */
    fun resolveAll(root: FileHandle, choice: ConflictChoice) {
        conflicts.forEach { resolve(root, it, choice) }
    }

    /** Asks a running delete or restore to stop before its next file; the file in progress is finished first. */
    fun onCancel() {
        stopRequested = true
    }
}

/** Runs [work] off the UI thread with [state] showing a progress bar and a Stop button. */
private suspend fun <T> runWithProgress(
    state: ActionUiState,
    isStopRequested: () -> Boolean,
    work: (JobControl) -> T,
): T {
    state.busy = true
    state.canCancel = true
    val control = object : JobControl {
        override fun shouldStop(): Boolean = isStopRequested()

        override fun report(done: Int, total: Int, current: String) {
            state.progress = done.toFloat() / total
            state.progressText = progressText(JobProgress(done, total, current))
        }
    }
    val result = withContext(Dispatchers.IO) { work(control) }
    state.progress = null
    state.progressText = ""
    state.canCancel = false
    state.busy = false
    return result
}
