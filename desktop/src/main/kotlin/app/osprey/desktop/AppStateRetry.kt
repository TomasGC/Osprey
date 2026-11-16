package app.osprey.desktop

import app.osprey.filesystem.FileHandle
import app.osprey.history.RetryPlanner
import app.osprey.history.RunRecord

/**
 * The scope a run is compared with to decide whether it can be retried (#83): the year selected now for a run that
 * ran on a year, none for one that did not.
 */
private fun AppState.retryScope(run: RunRecord): String? = run.origin?.scope?.let { selectedYear }

/** The question to ask before retrying [run], or null when none is needed: a Trash delete is irreversible. */
fun AppState.retryQuestion(run: RunRecord): String? =
    libraryHandle()?.let { trashRetry.question(it, run) }

/** True when the History page of [run] offers Retry failed: the library and year it ran on are the ones selected. */
fun AppState.canRetry(run: RunRecord): Boolean = RetryPlanner.canRetry(run, libraryLabel, retryScope(run))

/** Retries the files [run] failed on, in the action that ran it; the action does nothing unless [canRetry]. */
fun AppState.retry(run: RunRecord) {
    when (run.origin?.kind) {
        "reorg" -> libraryHandle()?.let { reorg.onRetry(it, run) }
        "trash-delete", "trash-restore" -> libraryHandle()?.let { trashRetry.retry(it, run) }
        else -> yearHandle()?.let { retryInYear(it, run) }
    }
}

private fun AppState.retryInYear(year: FileHandle, run: RunRecord) {
    val scope = retryScope(run)
    when (run.origin?.kind) {
        "flatten" -> flatten.onRetry(year, run, scope)
        "rename" -> rename.onRetry(year, run, scope)
        "dedupe" -> dedupe.onRetry(year, run, scope)
        "dates" -> dates.onRetry(year, run, scope)
        "standardize" -> standardize.onRetry(year, run, scope)
        "visual-dupes" -> visual.onRetry(year, run, scope)
    }
}
