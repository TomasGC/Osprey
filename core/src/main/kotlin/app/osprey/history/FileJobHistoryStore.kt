package app.osprey.history

import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * A [JobHistoryStore] that keeps each run in its own JSON-lines file in [dir] (#79), one line appended per event, so
 * a crash leaves a readable file. The logic is [InMemoryJobHistoryStore]'s; this class adds the files. A write that
 * fails loses that line, never the run: the screens read the runs from memory.
 */
class FileJobHistoryStore(dir: File) : JobHistoryStore {
    private val lock = Any()
    private val files = RunFiles(dir)
    private val memory = InMemoryJobHistoryStore(files.load())

    init {
        settle()
    }

    override val revision: StateFlow<Long> get() = memory.revision

    override fun begin(action: String, at: Instant): Long = synchronized(lock) {
        val id = memory.begin(action, at)
        files.append(id, RunLine.Header(id, action, at))
        settle()
        id
    }

    override fun start(id: Long, at: Instant) = recorded(id, RunLine.Started(at)) { memory.start(id, at) }

    override fun appendLog(id: Long, line: String) = recorded(id, RunLine.Logged(line)) { memory.appendLog(id, line) }

    override fun appendFile(id: Long, record: FileRecord) =
        recorded(id, RunLine.Filed(record)) { memory.appendFile(id, record) }

    override fun finish(id: Long, state: RunState, summary: String, at: Instant) =
        recorded(id, RunLine.Ended(state, summary, at)) { memory.finish(id, state, summary, at) }

    override fun runs(): List<RunRecord> = memory.runs()

    override fun closeInterrupted(at: Instant) = synchronized(lock) {
        val active = memory.runs().filter { it.state.isActive }
        memory.closeInterrupted(at)
        active.forEach { files.append(it.id, RunLine.Ended(RunState.Interrupted, "", at)) }
        settle()
    }

    override fun clear() = synchronized(lock) {
        memory.clear()
        settle()
    }

    private fun recorded(id: Long, line: RunLine, change: () -> Unit) = synchronized(lock) {
        if (memory.runs().any { it.id == id }) {
            change()
            files.append(id, line)
            settle()
        }
    }

    /** Deletes the files of the runs the memory store no longer keeps (past the limit, or cleared). */
    private fun settle() = files.keepOnly(memory.runs().map { it.id }.toSet())
}
