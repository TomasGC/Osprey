package app.osprey.desktop

import app.osprey.filesystem.FileHandle
import app.osprey.history.RetryPlanner
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.jobs.JobControl
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.appliedSuffix
import app.osprey.jobs.applyOutcome
import app.osprey.jobs.retryCounts
import app.osprey.jobs.scopedName
import app.osprey.jobs.underFolder

/**
 * Scan-then-apply bookkeeping shared by the simple action tabs: queue [scan] as a job, cache its plan, and
 * consume that exact cached plan on [onApply] as another job -- never re-scanning. Only this UI-side bookkeeping
 * is shared; the actions' own planners stay fully separate (what each needs to plan/describe/apply isn't uniform).
 *
 * Both run as jobs of [jobs] under [name], so they show progress, can be stopped and wait their turn. The result
 * is written to [state] by the job itself, so it lands even if the screen it was started from is gone.
 * [onApplied] is called once an apply is over, whether or not it finished.
 */
class SimpleActionController<Plan>(
    private val name: String,
    private val jobs: JobQueue,
    private val scan: (FileHandle, JobControl) -> ScanResult<Plan>,
    private val apply: (FileHandle, Plan, JobControl) -> Int,
    private val onApplied: () -> Unit = {},
    private val retry: RetryWiring<Plan>? = null,
) {
    val state = ActionUiState()
    private var cachedPlan: Plan? = null

    fun onScan(yearDir: FileHandle, scope: String? = null) {
        if (!claim()) return
        val label = scopedName(name, scope)
        state.appliedSuffix = ""
        jobs.enqueue("$label scan", onDone = { state.busy = false }, writes = false) { control ->
            val result = scan(yearDir, control)
            if (control.shouldStop()) {
                cachedPlan = null
                state.summary = "Scan stopped"
                state.lines = emptyList()
                state.applyEnabled = false
                JobOutcome("$label scan: stopped", stopped = true)
            } else {
                cachedPlan = result.plan
                state.lines = result.lines
                state.summary = result.summary
                state.applyEnabled = result.count > 0
                JobOutcome("$label scan: ${result.summary.trim()}", lines = result.lines)
            }
        }
    }

    fun onApply(yearDir: FileHandle, scope: String? = null) {
        val plan = cachedPlan ?: return
        if (!claim()) return
        val label = scopedName(name, scope)
        cachedPlan = null
        state.applyEnabled = false
        jobs.enqueue(
            "$label apply",
            onDone = { state.busy = false; onApplied() },
            origin = retry?.let { RunOrigin(it.kind, it.library(), scope) },
        ) { control ->
            val applied = apply(yearDir, plan, control.underFolder(scope))
            val stopped = control.shouldStop()
            val failed = control.failedFiles
            state.appliedSuffix = appliedSuffix(applied, failed, stopped)
            applyOutcome(label, applied, failed, stopped)
        }
    }

    /**
     * Retries the files [run] failed on (#83), as a run of its own: scans again, keeps the plan's items for those
     * files, applies them, and reports as skipped any failed file the new scan no longer holds. Nothing happens unless
     * [RetryPlanner.canRetry] says the run can be retried on the library and [scope] selected now.
     */
    fun onRetry(yearDir: FileHandle, run: RunRecord, scope: String? = null) {
        val wiring = retry ?: return
        val filter = wiring.filter
        if (!RetryPlanner.canRetry(run, wiring.library(), scope) || !claim()) return
        val label = scopedName(name, scope)
        jobs.enqueue(
            "$label retry",
            onDone = { state.busy = false; onApplied() },
            origin = RetryPlanner.retryOrigin(run),
        ) { control ->
            val placed = control.underFolder(scope)
            val failed = RetryPlanner.failedNames(run)
            val plan = scan(yearDir, control).plan
            if (control.shouldStop()) return@enqueue JobOutcome("$label retry: stopped", stopped = true)
            RetryPlanner.missingRecords(failed, filter.names(plan)).forEach(placed::record)
            val todo = filter.only(plan, failed)
            val applied = if (filter.names(todo).isEmpty()) 0 else apply(yearDir, todo, placed)
            val stopped = control.shouldStop()
            val failures = placed.failedFiles
            state.appliedSuffix = appliedSuffix(applied, failures, stopped)
            JobOutcome(
                RetryPlanner.retrySummary(run, retryCounts(applied, failures, stopped)),
                stopped = stopped,
                failures = failures,
            )
        }
    }

    private fun claim(): Boolean {
        if (state.busy) return false
        state.busy = true
        return true
    }
}
