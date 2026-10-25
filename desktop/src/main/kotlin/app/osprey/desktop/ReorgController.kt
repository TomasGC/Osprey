package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlan
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.reorg.reorgResultLines
import app.osprey.actions.reorg.reorgSummary
import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue

/**
 * Scan-then-apply for Reorg (#40), as two jobs of [jobs]. Unlike the simple actions it runs on the folders saved
 * in Settings, reports progress file by file and can be stopped between two files; a file that fails is listed and
 * the others carry on. [onApplied] is called once an apply is over, so the year list can be read again.
 */
class ReorgController(
    private val planner: ReorgPlanner,
    private val jobs: JobQueue,
    private val sources: () -> List<ReorgSource>,
    private val onApplied: () -> Unit = {},
) {
    val state = ActionUiState()
    private var cachedPlan: ReorgPlan? = null

    fun onScan(output: FileHandle) {
        if (!claim()) return
        state.appliedSuffix = ""
        jobs.enqueue("Reorg scan", onDone = { state.busy = false }) { control ->
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
                state.applyEnabled = count > 0
                JobOutcome("Reorg scan: ${state.summary.trim()}", lines = lines)
            }
        }
    }

    fun onApply(output: FileHandle) {
        val plan = cachedPlan ?: return
        if (!claim()) return
        cachedPlan = null
        state.applyEnabled = false
        jobs.enqueue("Reorg apply", onDone = { state.busy = false; onApplied() }) { control ->
            val result = planner.apply(plan, output, control)
            val resultLines = reorgResultLines(result)
            state.lines = resultLines + state.lines
            state.appliedSuffix = "  -- APPLIED (${result.done}), failed ${result.failures.size}"
            JobOutcome(
                summary = "Reorg: ${result.done} done, ${result.failures.size} failed",
                lines = resultLines,
                failures = result.failures.size,
                stopped = result.cancelled,
            )
        }
    }

    private fun claim(): Boolean {
        if (state.busy) return false
        state.busy = true
        return true
    }
}
