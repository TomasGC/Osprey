package app.osprey.history

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [JobHistoryStore] in memory: the fake for tests, and the logic [FileJobHistoryStore] builds on. [restored] are the
 * runs a previous launch left: those [runsToDrop] lists at [now] are dropped, and new ids go on after the highest one.
 * After that only the cap on file records ([runsOverFileCap], [maxFileRecords]) drops a run while the app runs (#109).
 */
class InMemoryJobHistoryStore(
    restored: List<RunRecord> = emptyList(),
    now: Instant = Instant.now(),
    private val maxFileRecords: Int = MAX_FILE_RECORDS,
) : JobHistoryStore {
    private val lock = Any()

    private val revisionState = MutableStateFlow(0L)

    override val revision: StateFlow<Long> = revisionState.asStateFlow()

    private var nextId = (restored.maxOfOrNull { it.id } ?: 0L) + 1
    private var kept: List<RunRecord> = restored.sortedBy { it.id }.let { sorted -> // oldest first
        val drop = runsToDrop(sorted, now, maxFileRecords).toSet()
        sorted.filterNot { it.id in drop }
    }

    override fun begin(action: String, at: Instant): Long = synchronized(lock) {
        val id = nextId++
        commit(kept + RunRecord(id, action, RunState.Queued, queuedAt = at))
        id
    }

    override fun start(id: Long, at: Instant) =
        change(id) { it.copy(state = RunState.Running, startedAt = at) }

    override fun appendLog(id: Long, line: String) = change(id) { it.copy(log = it.log + line) }

    override fun appendFile(id: Long, record: FileRecord) = change(id) { it.copy(files = it.files + record) }

    override fun finish(id: Long, state: RunState, summary: String, at: Instant) =
        change(id) { it.copy(state = state, summary = summary, endedAt = at) }

    override fun runs(): List<RunRecord> = synchronized(lock) { kept.reversed() }

    override fun closeInterrupted(at: Instant) = synchronized(lock) {
        commit(kept.map { if (it.state.isActive) it.copy(state = RunState.Interrupted, endedAt = at) else it })
    }

    override fun remove(ids: Set<Long>) = synchronized(lock) {
        commit(kept.filterNot { it.id in ids && !it.state.isActive })
    }

    private fun change(id: Long, edit: (RunRecord) -> RunRecord) = synchronized(lock) {
        commit(kept.map { if (it.id == id) edit(it) else it })
    }

    /** Keeps [next] minus the runs over the cap on file records, and tells the screens something changed. */
    private fun commit(next: List<RunRecord>) {
        val drop = runsOverFileCap(next, maxFileRecords).toSet()
        kept = next.filterNot { it.id in drop }
        revisionState.update { it + 1 }
    }
}
