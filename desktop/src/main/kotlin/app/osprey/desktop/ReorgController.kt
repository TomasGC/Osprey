package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlan
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.reorg.reorgResultLines
import app.osprey.actions.reorg.reorgSummary
import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scan-then-apply for Reorg (#40). Unlike the simple actions it runs on the folders saved in
 * Settings, reports progress file by file, and can be asked to stop between two files; a file that
 * fails is listed and the others carry on.
 */
class ReorgController(
    private val planner: ReorgPlanner,
    private val sources: () -> List<ReorgSource>,
) {
    val state = ActionUiState()
    private var cachedPlan: ReorgPlan? = null

    @Volatile
    private var stopRequested = false

    suspend fun onScan(output: FileHandle) {
        state.busy = true
        state.appliedSuffix = ""
        state.progress = null
        state.progressText = "Scanning..."
        val plan = withContext(Dispatchers.IO) { planner.plan(output, sources()) }
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        cachedPlan = plan
        state.lines = lines
        state.summary = reorgSummary(plan)
        state.applyEnabled = count > 0
        state.progressText = ""
        state.busy = false
    }

    suspend fun onApply(output: FileHandle) {
        val plan = cachedPlan ?: return
        cachedPlan = null
        stopRequested = false
        state.applyEnabled = false
        state.busy = true
        state.canCancel = true
        val result = withContext(Dispatchers.IO) {
            planner.apply(plan, output, shouldStop = { stopRequested }) { progress ->
                state.progress = progress.done.toFloat() / progress.total
                state.progressText = "${progress.done + 1} of ${progress.total}: ${progress.current}"
            }
        }
        state.lines = reorgResultLines(result) + state.lines
        state.appliedSuffix = "  -- APPLIED (${result.done}), failed ${result.failures.size}"
        state.progress = null
        state.progressText = ""
        state.canCancel = false
        state.busy = false
    }

    /** Asks a running apply to stop before its next file; the file in progress is finished first. */
    fun onCancel() {
        stopRequested = true
    }
}
