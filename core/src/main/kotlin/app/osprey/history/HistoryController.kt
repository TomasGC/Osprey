package app.osprey.history

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FOLLOW_INTERVAL_MS = 500L

/** How long a removal waits for an Undo before the store forgets the runs (#110). */
const val UNDO_WINDOW_MS = 8_000L

/**
 * What the History screen shows: the runs by state, the run whose page is open, if any, the runs checked for removal,
 * how many runs were just removed and can still be brought back, and which removal that is: a screen restarts its
 * timer when [removalId] changes.
 */
data class HistoryView(
    val sections: List<RunSection>,
    val selected: RunRecord?,
    val checked: Set<Long> = emptySet(),
    val removed: Int = 0,
    val removalId: Long = 0,
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    /** True while at least one run is checked: a tap then checks or unchecks instead of opening. */
    val selecting: Boolean get() = checked.isNotEmpty()

    /** What the clear button says: it removes the checked runs, or else every successful one. */
    val clearLabel: String get() = if (selecting) "Clear selected (${checked.size})" else "Clear successful"

    val clearEnabled: Boolean get() = selecting || sections.any { it.state == RunState.Done }

    /** The notice to show with an Undo, or null when no removal waits. */
    val removedText: String?
        get() = when (removed) {
            0 -> null
            1 -> "Run removed"
            else -> "$removed runs removed"
        }
}

/**
 * The logic of the History screen (#79, #110), shared by both platforms: they only draw [view]. A removal hides the
 * runs at once and the store forgets them at [commit]; [undo] brings them back before that.
 */
class HistoryController(private val store: JobHistoryStore) {
    private var selectedId: Long? = null
    private var checked: Set<Long> = emptySet()
    private var hidden: Set<Long> = emptySet()
    private var visible: List<RunRecord> = emptyList()
    private var removalId = 0L
    private val state = MutableStateFlow(HistoryView(emptyList(), null))
    val view: StateFlow<HistoryView> = state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reads the store again. The open run follows its new state; a run that is gone closes the page or is unchecked;
     * a run waiting for its removal stays hidden.
     */
    fun refresh() {
        visible = store.runs().filterNot { it.id in hidden }
        checked = checked intersect visible.filterNot { it.state.isActive }.map { it.id }.toSet()
        val open = visible.firstOrNull { it.id == selectedId }
        state.value = HistoryView(runSections(visible), open, checked, hidden.size, removalId)
    }

    fun open(id: Long) {
        selectedId = id
        refresh()
    }

    /** Back: unchecks every run if some are checked, else closes the open run's page. */
    fun back() {
        if (checked.isEmpty()) selectedId = null else checked = emptySet()
        refresh()
    }

    /** A tap on a run: opens it, or checks or unchecks it while some run is checked. */
    fun press(id: Long) {
        if (checked.isEmpty()) open(id) else longPress(id)
    }

    /** A long press on a run: checks or unchecks it. A queued or running run cannot be checked. */
    fun longPress(id: Long) {
        val run = visible.firstOrNull { it.id == id } ?: return
        if (run.state.isActive) return
        checked = if (id in checked) checked - id else checked + id
        refresh()
    }

    /** Removes the checked runs, or else every successful one (state Done); the others stay. */
    fun clear() {
        val done = visible.filter { it.state == RunState.Done }.map { it.id }.toSet()
        remove(if (checked.isEmpty()) done else checked)
    }

    /**
     * Hides the ended runs in [ids] until [commit] or [undo]. A removal still waiting is applied first; a queued or
     * running run, or one that is not shown, is ignored.
     */
    fun remove(ids: Set<Long>) {
        val removable = visible.filter { it.id in ids && !it.state.isActive }.map { it.id }.toSet()
        if (removable.isEmpty()) return
        commit()
        hidden = removable
        removalId++
        checked = emptySet()
        refresh()
    }

    /** Brings back the runs a removal hid. */
    fun undo() {
        hidden = emptySet()
        refresh()
    }

    /** Makes the removal final: the store forgets the hidden runs, and their files go. */
    fun commit() {
        if (hidden.isEmpty()) return
        store.remove(hidden)
        hidden = emptySet()
        refresh()
    }

    /**
     * Keeps [view] current while the screen is shown: reads the store again when it changes, at most once per
     * [intervalMs], so a run that writes thousands of file records does not make the screen redraw thousands of times.
     */
    suspend fun follow(intervalMs: Long = FOLLOW_INTERVAL_MS) {
        store.revision.collect {
            refresh()
            delay(intervalMs)
        }
    }
}
