package app.osprey.history

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FOLLOW_INTERVAL_MS = 500L

/** What the History screen shows: the runs by state, and the run whose page is open, if any. */
data class HistoryView(val sections: List<RunSection>, val selected: RunRecord?) {
    val isEmpty: Boolean get() = sections.isEmpty()

    /** How many runs have ended: the ones Clear history forgets. */
    val endedCount: Int get() = sections.filterNot { it.state.isActive }.sumOf { it.runs.size }
}

/** The logic of the History screen (#79), shared by both platforms: they only draw [view]. */
class HistoryController(private val store: JobHistoryStore) {
    private var selectedId: Long? = null
    private val state = MutableStateFlow(HistoryView(emptyList(), null))
    val view: StateFlow<HistoryView> = state.asStateFlow()

    init {
        refresh()
    }

    /** Reads the store again. The open run follows its new state; a run that is gone closes the page. */
    fun refresh() {
        val runs = store.runs()
        state.value = HistoryView(runSections(runs), runs.firstOrNull { it.id == selectedId })
    }

    fun open(id: Long) {
        selectedId = id
        refresh()
    }

    fun back() {
        selectedId = null
        refresh()
    }

    /** Forgets every run that has ended (the store keeps a queued or running one). */
    fun clear() {
        store.clear()
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
