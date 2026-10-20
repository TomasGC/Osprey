package app.osprey.desktop

import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scan-then-apply bookkeeping shared by the five simple action tabs: run [scan] on a background
 * thread, cache its plan, and consume that exact cached plan on [onApply] -- never re-scanning.
 * Matches the POC's `_last_plan` cache-and-pop behavior in `app.py`'s generic `scan()`/`apply()`.
 *
 * Only this UI-side bookkeeping is shared; the five actions' own planners stay fully separate
 * (what each needs to plan/describe/apply isn't uniform, same reasoning the POC's own adapter
 * comment gives for not forcing a common interface onto them).
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
