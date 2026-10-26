package app.osprey.desktop

import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobControl
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.withStoppedNote

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
) {
    val state = ActionUiState()
    private var cachedPlan: Plan? = null

    fun onScan(yearDir: FileHandle) {
        if (!claim()) return
        state.appliedSuffix = ""
        jobs.enqueue("$name scan", onDone = { state.busy = false }) { control ->
            val result = scan(yearDir, control)
            if (control.shouldStop()) {
                cachedPlan = null
                state.summary = "Scan stopped"
                state.lines = emptyList()
                state.applyEnabled = false
                JobOutcome("$name scan: stopped", stopped = true)
            } else {
                cachedPlan = result.plan
                state.lines = result.lines
                state.summary = result.summary
                state.applyEnabled = result.count > 0
                JobOutcome("$name scan: ${result.summary.trim()}", lines = result.lines)
            }
        }
    }

    fun onApply(yearDir: FileHandle) {
        val plan = cachedPlan ?: return
        if (!claim()) return
        cachedPlan = null
        state.applyEnabled = false
        jobs.enqueue("$name apply", onDone = { state.busy = false; onApplied() }) { control ->
            val applied = apply(yearDir, plan, control)
            val stopped = control.shouldStop()
            state.appliedSuffix = "  -- APPLIED ($applied)" + if (stopped) " -- STOPPED" else ""
            JobOutcome(withStoppedNote("$name apply: $applied done", stopped), stopped = stopped)
        }
    }

    private fun claim(): Boolean {
        if (state.busy) return false
        state.busy = true
        return true
    }
}
