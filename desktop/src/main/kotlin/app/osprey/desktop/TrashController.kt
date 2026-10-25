package app.osprey.desktop

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
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue

/**
 * The Trash screen (#41): what the Trash holds by year and action, the files ticked across its tabs, and
 * the two things that can be done with them. Delete is the only irreversible step in Osprey, so it
 * waits for [confirmation] to be accepted; Restore moves files back and hands the name clashes to
 * [conflicts] for the user to settle. Both run as jobs of [jobs]: they report progress per file and can be
 * stopped between two. [onChanged] is called once one is over, so the year list can be read again.
 */
class TrashController(
    private val fs: FileSystemRepository,
    private val jobs: JobQueue,
    private val onChanged: () -> Unit = {},
) {

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

    fun confirmDelete(root: FileHandle) {
        confirmation = null
        val selected = selection.entriesIn(contents)
        if (selected.isEmpty() || !claim()) return
        jobs.enqueue("Trash delete", onDone = { state.busy = false; onChanged() }) { control ->
            val result = TrashClearer(fs).clear(root, selected, control)
            report = clearResultLines(result)
            refresh(root)
            JobOutcome(
                summary = "Trash: ${result.deleted} deleted, ${result.failures.size} failed",
                lines = report,
                failures = result.failures.size,
                stopped = result.cancelled,
            )
        }
    }

    fun restore(root: FileHandle) {
        val selected = selection.entriesIn(contents)
        if (selected.isEmpty() || !claim()) return
        jobs.enqueue("Trash restore", onDone = { state.busy = false; onChanged() }) { control ->
            val restorer = TrashRestorer(fs)
            val plan = restorer.plan(root, selected)
            val result = restorer.restore(root, plan, control)
            conflicts = plan.conflicts
            report = restoreResultLines(result, plan.conflicts.size)
            refresh(root)
            JobOutcome(
                summary = "Trash: ${result.restored} restored, ${result.failures.size} failed",
                lines = report,
                failures = result.failures.size,
                stopped = result.cancelled,
            )
        }
    }

    private fun claim(): Boolean {
        if (state.busy) return false
        state.busy = true
        return true
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
}
