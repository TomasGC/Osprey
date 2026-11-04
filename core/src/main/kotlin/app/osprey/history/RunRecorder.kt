package app.osprey.history

import app.osprey.jobs.JobOutcome
import java.time.Instant

/**
 * Writes one run into a [JobHistoryStore] as it happens (#79). The history is a record, never a requirement: a store
 * that cannot write loses the record, not the job, and each write stands alone, so a log line that cannot be written
 * does not leave the run looking active.
 */
class RunRecorder(
    private val history: JobHistoryStore,
    private val clock: () -> Instant = Instant::now,
) {

    /** Records a new queued run; the returned [Run] is safe to use even when the store failed. */
    fun begin(action: String): Run = Run(recorded { history.begin(action, clock()) })

    inner class Run internal constructor(private val id: Long?) {

        fun start() = write { history.start(it, clock()) }

        fun file(record: FileRecord) = write { history.appendFile(it, record) }

        /** Closes the run with the way [outcome] ended: stopped, failed files, or done. */
        fun end(outcome: JobOutcome) {
            outcome.lines.forEach { line -> write { history.appendLog(it, line) } }
            write { history.finish(it, outcome.runState(), outcome.summary, clock()) }
        }

        private fun write(action: (Long) -> Unit) {
            id?.let { recorded { action(it) } }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun <T> recorded(write: () -> T): T? = try {
        write()
    } catch (ignored: Exception) {
        null
    }
}

private fun JobOutcome.runState(): RunState = when {
    stopped -> RunState.Stopped
    failures > 0 -> RunState.Failed
    else -> RunState.Done
}
