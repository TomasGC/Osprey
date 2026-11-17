package app.osprey.history

private const val MISSING_REASON = "no longer there or already handled"

/** What a retry of the failed files of a run needs (#83): when it is offered, and the pieces of the new run. */
object RetryPlanner {

    /** The kinds of run that can be retried: the `kind` of their [RunOrigin]. */
    val kinds: List<String> = listOf(
        "flatten", "rename", "dedupe", "dates", "standardize", "reorg", "trash-delete", "trash-restore", "visual-dupes",
    )

    /** The paths [run] recorded as failed: a skipped or a done file is not retried. */
    fun failedPaths(run: RunRecord): Set<String> =
        run.files.filter { it.result == FileResult.Failed }.map { it.path }.toSet()

    /** What the button on the page of [run] says: how many failed files it would retry. */
    fun retryLabel(run: RunRecord): String = "Retry failed (${failedPaths(run).size})"

    /** The names [run] failed on, as a plan records them: [failedPaths] without the scope folder in front. */
    fun failedNames(run: RunRecord): Set<String> {
        val prefix = run.origin?.scope?.let { "$it/" }.orEmpty()
        return failedPaths(run).map { it.removePrefix(prefix) }.toSet()
    }

    /**
     * True when the page of [run] offers Retry failed: it ended as Failed (not stopped, interrupted or still going),
     * kept some failed files, knows where it came from, is of a kind that can be retried, and ran on the [library] and
     * [scope] that are selected now.
     */
    fun canRetry(run: RunRecord, library: String, scope: String?): Boolean {
        val origin = run.origin
        return origin != null &&
            origin.kind in kinds &&
            run.state == RunState.Failed &&
            failedPaths(run).isNotEmpty() &&
            origin.library == library &&
            origin.scope == scope
    }

    /** The origin of the run that retries [run]: the same kind, library and scope, pointing back at [run]. */
    fun retryOrigin(run: RunRecord): RunOrigin? = run.origin?.copy(retryOf = run.id)

    /** The summary of the retry of [run], saying which run it retries. */
    fun retrySummary(run: RunRecord, summary: String): String = "Retry of run #${run.id}: $summary"

    /**
     * The records for the [failed] paths that the rebuilt plan no longer holds: the file moved, changed or is gone
     * since the first run, so the retry reports it as skipped instead of failing. In path order.
     */
    fun missingRecords(failed: Set<String>, planned: Collection<String>): List<FileRecord> =
        failed.filterNot { it in planned }.sorted().map { FileRecord(it, FileResult.Skipped, MISSING_REASON) }
}
