package app.osprey.android

import app.osprey.actions.trash.TrashRetrier
import app.osprey.actions.trash.clearOutcome
import app.osprey.actions.trash.restoreOutcome
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.RetryPlanner
import app.osprey.history.RunRecord
import app.osprey.jobs.JobQueue

private const val DELETE = "trash-delete"
private const val RESTORE = "trash-restore"

/**
 * The retry of the failed files of a Trash delete or restore (#83), as a run of its own. A delete is irreversible, so
 * [question] gives the confirmation to ask before [retry] runs one; a restore asks nothing. [onChanged] is called once
 * a retry is over, so the year list can be read again.
 */
class TrashRetryController(
    fs: FileSystemRepository,
    private val jobs: JobQueue,
    private val trash: TrashController,
    private val onChanged: () -> Unit = {},
    private val library: () -> String,
) {
    private val retrier = TrashRetrier(fs)

    /** The question to ask before retrying [run], or null when none is needed. */
    fun question(root: FileHandle, run: RunRecord): String? =
        if (run.origin?.kind == DELETE && RetryPlanner.canRetry(run, library(), null)) {
            retrier.deleteQuestion(root, RetryPlanner.failedNames(run))
        } else {
            null
        }

    /** Retries the files [run] failed on; nothing happens unless the run can be retried on this library. */
    fun retry(root: FileHandle, run: RunRecord) {
        val kind = run.origin?.kind
        if ((kind != DELETE && kind != RESTORE) || !RetryPlanner.canRetry(run, library(), null)) return
        if (trash.state.busy) return
        trash.state.busy = true
        jobs.enqueue(
            if (kind == DELETE) "Trash delete retry" else "Trash restore retry",
            onDone = { trash.state.busy = false; onChanged() },
            origin = RetryPlanner.retryOrigin(run),
        ) { control ->
            val failed = RetryPlanner.failedNames(run)
            val outcome = if (kind == DELETE) {
                clearOutcome(retrier.delete(root, failed, control))
            } else {
                restoreOutcome(retrier.restore(root, failed, control), conflicts = 0)
            }
            trash.refresh(root)
            outcome.copy(summary = RetryPlanner.retrySummary(run, outcome.summary))
        }
    }
}
