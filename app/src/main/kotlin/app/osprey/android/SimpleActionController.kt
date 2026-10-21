package app.osprey.android

import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scan-then-apply bookkeeping shared by the five simple action screens: run [scan] on a
 * background thread, cache its plan, and consume that exact cached plan on [onApply] -- never
 * re-scanning. Same contract as desktop's controller of the same name (#11).
 */
class SimpleActionController<Plan>(
    private val scan: (FileHandle) -> ScanResult<Plan>,
    private val apply: (FileHandle, Plan) -> Int,
) {
    val state = ActionUiState()
    private var cachedPlan: Plan? = null

    suspend fun onScan(yearDir: FileHandle) {
        state.busy = true
        state.appliedSuffix = ""
        val result = withContext(Dispatchers.IO) { scan(yearDir) }
        cachedPlan = result.plan
        state.lines = result.lines
        state.summary = result.summary
        state.applyEnabled = result.count > 0
        state.busy = false
    }

    suspend fun onApply(yearDir: FileHandle) {
        val plan = cachedPlan ?: return
        cachedPlan = null
        state.applyEnabled = false
        state.busy = true
        val applied = withContext(Dispatchers.IO) { apply(yearDir, plan) }
        state.busy = false
        state.appliedSuffix = "  -- APPLIED ($applied)"
    }
}
