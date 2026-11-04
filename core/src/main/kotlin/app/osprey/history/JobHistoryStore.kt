package app.osprey.history

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** Most runs kept (#79); the oldest ended run goes first. */
const val MAX_RUNS = 10

/**
 * Where the runs are kept between launches: a JSON-lines file per run on each platform, memory in tests. A call about
 * an unknown id does nothing. Every write must survive a crash, so a run cut short is still readable.
 */
interface JobHistoryStore {

    /** Records a new queued run and returns its id: unique among the runs kept, and higher than every one of them. */
    fun begin(action: String, at: Instant): Long

    fun start(id: Long, at: Instant)

    fun appendLog(id: Long, line: String)

    fun appendFile(id: Long, record: FileRecord)

    fun finish(id: Long, state: RunState, summary: String, at: Instant)

    /** Every kept run, newest first. */
    fun runs(): List<RunRecord>

    /** Rises on every write, so a screen showing the history knows when to read it again. */
    val revision: StateFlow<Long>

    /** Marks every run still queued or running as [RunState.Interrupted]: called once when the app starts. */
    fun closeInterrupted(at: Instant)

    /** Forgets every run that has ended; a queued or running one stays. */
    fun clear()
}

/**
 * The ids to forget so that at most [max] runs stay: the oldest ended ones.
 * A queued or running run is never listed.
 */
fun runsToDrop(runs: List<RunRecord>, max: Int = MAX_RUNS): List<Long> {
    val excess = runs.size - max
    if (excess <= 0) return emptyList()
    return runs.filterNot { it.state.isActive }.sortedBy { it.id }.take(excess).map { it.id }
}
