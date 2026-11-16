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
    val scope = retryScope(run)
    when (run.origin?.kind) {
        "reorg" -> libraryHandle()?.let { reorg.onRetry(it, run) }
        "flatten" -> yearHandle()?.let { flatten.onRetry(it, run, scope) }
        "rename" -> yearHandle()?.let { rename.onRetry(it, run, scope) }
        "dedupe" -> yearHandle()?.let { dedupe.onRetry(it, run, scope) }
        "dates" -> yearHandle()?.let { dates.onRetry(it, run, scope) }
        "standardize" -> yearHandle()?.let { standardize.onRetry(it, run, scope) }
    }
}
