package app.osprey.desktop

import app.osprey.actions.reorg.DuplicateChoice
import app.osprey.actions.reorg.DuplicateReview
import app.osprey.actions.reorg.REORG_RETRY
import app.osprey.actions.reorg.ReorgPlan
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgResult
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.reorg.reorgResultLines
import app.osprey.actions.reorg.reorgSummary
import app.osprey.actions.reorg.retryChoices
import app.osprey.filesystem.FileHandle
import app.osprey.history.RetryPlanner
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.withStoppedNote

/**
 * Scan-then-apply for Reorg (#40), as two jobs of [jobs]. Unlike the simple actions it runs on the folders saved
 * in Settings, reports progress file by file and can be stopped between two files; a file that fails is listed and
 * the others carry on. [onApplied] is called once an apply is over, so the year list can be read again.
 */
class ReorgController(
    private val planner: ReorgPlanner,
    private val jobs: JobQueue,
    private val sources: () -> List<ReorgSource>,
    private val library: () -> String = { "" },
    private val onApplied: () -> Unit = {},
) {
    val state = ActionUiState()

    /** The "Which files do you want to keep?" dialog (#91); the screen draws it while it is open. */
    val review = DuplicateReview()
    private var cachedPlan: ReorgPlan? = null

    fun onScan(output: FileHandle) {
        if (!claim()) return
        review.cancel()
        state.appliedSuffix = ""
        jobs.enqueue("Reorg scan", onDone = { state.busy = false }, writes = false) { control ->
            val plan = planner.plan(output, sources(), control)
            if (control.shouldStop()) {
                cachedPlan = null
                state.summary = "Scan stopped"
                state.lines = emptyList()
                state.applyEnabled = false
                JobOutcome("Reorg scan: stopped", stopped = true)
            } else {
                val lines = mutableListOf<String>()
                val count = planner.describe(plan, lines::add)
                cachedPlan = plan
                state.lines = lines
                state.summary = reorgSummary(plan)
                state.applyEnabled = count + plan.duplicates.size > 0
                JobOutcome("Reorg scan: ${state.summary.trim()}", lines = lines)
            }
        }
    }

    /** Apply: a plan with duplicates first asks which files to keep (see [review]); otherwise it runs at once. */
    fun onApply(output: FileHandle) {
        val plan = cachedPlan ?: return
        if (plan.duplicates.isNotEmpty()) review.open(plan.duplicates) else apply(plan, output, emptyList())
    }

    /** Continue in the review: runs the apply with what the user ticked. */
    fun onContinue(output: FileHandle) {
        val plan = cachedPlan ?: return
        if (state.busy) return
        val choices = review.confirm() ?: return
        apply(plan, output, choices)
    }

    /**
     * Retries the files [run] failed on (#83), as a run of its own: scans the inputs again, keeps the items and pairs
     * of those files, applies them, and reports as skipped any failed file the new scan no longer holds. Nothing
     * happens unless [RetryPlanner.canRetry] says the run can be retried on the library selected now.
     */
    fun onRetry(output: FileHandle, run: RunRecord) {
        if (!RetryPlanner.canRetry(run, library(), null) || !claim()) return
        jobs.enqueue(
            "Reorg retry",
            onDone = { state.busy = false; onApplied() },
            origin = RetryPlanner.retryOrigin(run),
        ) { control ->
            val failed = RetryPlanner.failedNames(run)
            val plan = planner.plan(output, sources(), control)
            if (control.shouldStop()) return@enqueue JobOutcome("Reorg retry: stopped", stopped = true)
            RetryPlanner.missingRecords(failed, REORG_RETRY.names(plan)).forEach(control::record)
            val todo = REORG_RETRY.only(plan, failed)
            val outcome = finish(planner.apply(todo, output, todo.retryChoices(failed), control))
            outcome.copy(summary = RetryPlanner.retrySummary(run, outcome.summary))
        }
    }

    /** Cancel in the review: nothing happens, the plan stays. */
    fun onCancel() {
        review.cancel()
    }

    private fun apply(plan: ReorgPlan, output: FileHandle, choices: List<DuplicateChoice>) {
        if (!claim()) return
        cachedPlan = null
        state.applyEnabled = false
        jobs.enqueue(
            "Reorg apply",
            onDone = { state.busy = false; onApplied() },
            origin = RunOrigin("reorg", library()),
        ) { control -> finish(planner.apply(plan, output, choices, control)) }
    }

    /** Writes what an apply did to the screen and returns it as the job's outcome. */
    private fun finish(result: ReorgResult): JobOutcome {
        val resultLines = reorgResultLines(result)
        state.lines = resultLines + state.lines
        state.appliedSuffix = "  -- APPLIED (${result.done}), failed ${result.failures.size}"
        val failed = result.failures.size
        return JobOutcome(
            summary = withStoppedNote("Reorg: ${result.done} done, $failed failed", result.cancelled),
            lines = resultLines,
            failures = failed,
            stopped = result.cancelled,
        )
    }

    private fun claim(): Boolean {
        if (state.busy) return false
        state.busy = true
        return true
    }
}
