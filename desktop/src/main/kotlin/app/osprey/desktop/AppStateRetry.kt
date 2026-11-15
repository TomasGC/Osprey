package app.osprey.desktop

import app.osprey.history.RetryPlanner
import app.osprey.history.RunRecord

/**
 * The scope a run is compared with to decide whether it can be retried (#83): the year selected now for a run that
 * ran on a year, none for one that did not.
 */
private fun AppState.retryScope(run: RunRecord): String? = run.origin?.scope?.let { selectedYear }

/** True when the History page of [run] offers Retry failed: the library and year it ran on are the ones selected. */
fun AppState.canRetry(run: RunRecord): Boolean = RetryPlanner.canRetry(run, libraryLabel, retryScope(run))

/** Retries the files [run] failed on, in the action that ran it; the action does nothing unless [canRetry]. */
fun AppState.retry(run: RunRecord) {
    val year = yearHandle() ?: return
    val scope = retryScope(run)
    when (run.origin?.kind) {
        "flatten" -> flatten.onRetry(year, run, scope)
        "rename" -> rename.onRetry(year, run, scope)
        "dedupe" -> dedupe.onRetry(year, run, scope)
        "dates" -> dates.onRetry(year, run, scope)
        "standardize" -> standardize.onRetry(year, run, scope)
    }
}
