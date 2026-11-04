package app.osprey.history

import java.time.Instant

/** A [JobHistoryStore] in memory: the fake for tests, and the store of a platform with no persistence yet. */
class InMemoryJobHistoryStore : JobHistoryStore {
    private val lock = Any()
    private var nextId = 1L
    private var kept = emptyList<RunRecord>() // oldest first

    override fun begin(action: String, at: Instant): Long = synchronized(lock) {
        val id = nextId++
        kept = kept + RunRecord(id, action, RunState.Queued, queuedAt = at)
        prune()
        id
    }

    override fun start(id: Long, at: Instant) =
        change(id) { it.copy(state = RunState.Running, startedAt = at) }

    override fun appendLog(id: Long, line: String) = change(id) { it.copy(log = it.log + line) }

    override fun appendFile(id: Long, record: FileRecord) = change(id) { it.copy(files = it.files + record) }

    override fun finish(id: Long, state: RunState, summary: String, at: Instant) {
        change(id) { it.copy(state = state, summary = summary, endedAt = at) }
        synchronized(lock) { prune() }
    }

    override fun runs(): List<RunRecord> = synchronized(lock) { kept.reversed() }

    override fun closeInterrupted(at: Instant) = synchronized(lock) {
        kept = kept.map {
            if (it.state.isActive) it.copy(state = RunState.Interrupted, endedAt = at) else it
        }
    }

    override fun clear() = synchronized(lock) { kept = kept.filter { it.state.isActive } }

    private fun change(id: Long, edit: (RunRecord) -> RunRecord) = synchronized(lock) {
        kept = kept.map { if (it.id == id) edit(it) else it }
    }

    private fun prune() {
        val drop = runsToDrop(kept).toSet()
        kept = kept.filterNot { it.id in drop }
    }
}
