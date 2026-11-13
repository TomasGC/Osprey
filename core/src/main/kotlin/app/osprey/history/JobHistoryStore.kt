package app.osprey.history

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** How long an ended run stays in the history (#109). */
private const val KEEP_AFTER_END_HOURS = 24L
val KEEP_AFTER_END: Duration = Duration.ofHours(KEEP_AFTER_END_HOURS)

/**
 * Most file records the ended runs may hold together (#109). Each record weighs about 650 bytes in memory and the whole
 * history is read at start: 50,000 is about 35 MB and a few seconds on the emulator, where 30 runs of 5,000 files
 * (150,000 records) took 96 MB and 20 s, and 60 such runs ran out of memory.
 */
const val MAX_FILE_RECORDS = 50_000

/** The ended runs that stay whatever their age, so a quiet weekend does not leave the history empty (#109). */
const val KEEP_NEWEST = 3

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
 * The ids to forget at the start, at [now]: the ended runs that ended [KEEP_AFTER_END] ago or more, then the oldest of
 * the others while they hold more than [maxRecords] file records ([runsOverFileCap]). The [KEEP_NEWEST] newest ended
 * runs always stay. A run with no end time counts from when it was queued. A queued or running run is never listed.
 */
fun runsToDrop(runs: List<RunRecord>, now: Instant, maxRecords: Int = MAX_FILE_RECORDS): List<Long> {
    val aged = runs.filterNot { it.state.isActive }
        .sortedBy { it.id }
        .dropLast(KEEP_NEWEST)
        .filter { (it.endedAt ?: it.queuedAt).plus(KEEP_AFTER_END) <= now }
        .map { it.id }
    return aged + runsOverFileCap(runs.filterNot { it.id in aged }, maxRecords)
}

/**
 * The ids to forget so that the ended runs hold at most [maxRecords] file records: the oldest first, never one of the
 * [KEEP_NEWEST] newest ended runs, even when they alone are over. Queued and running runs are not counted and never
 * listed. Used after every write, since a session that keeps running must not grow without end either.
 */
fun runsOverFileCap(runs: List<RunRecord>, maxRecords: Int): List<Long> {
    val ended = runs.filterNot { it.state.isActive }.sortedBy { it.id }
    var total = ended.sumOf { it.files.size }
    val drop = mutableListOf<Long>()
    for (run in ended.dropLast(KEEP_NEWEST)) {
        if (total <= maxRecords) break
        total -= run.files.size
        drop += run.id
    }
    return drop
}
