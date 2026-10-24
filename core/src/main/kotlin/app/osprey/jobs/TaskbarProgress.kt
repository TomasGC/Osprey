package app.osprey.jobs

private const val FULL_PERCENT = 100

/** What a taskbar button can show. */
enum class TaskbarState { NONE, INDETERMINATE, NORMAL, ERROR }

/** The taskbar button's state and, for [TaskbarState.NORMAL] and [TaskbarState.ERROR], how full it is. */
data class TaskbarProgress(val state: TaskbarState, val percent: Int)

/**
 * What the taskbar should show for [entries]: the running job's progress, an unknown amount while jobs
 * only wait, or an error once the latest finished job had failures. A job the user stopped is not an error.
 */
fun taskbarProgress(entries: List<JobEntry>): TaskbarProgress {
    val running = entries.map { it.state }.filterIsInstance<JobState.Running>().firstOrNull()
    if (running != null) return runningProgress(running)
    if (entries.any { it.state == JobState.Queued }) return TaskbarProgress(TaskbarState.INDETERMINATE, 0)
    val latest = entries.map { it.state }.filterIsInstance<JobState.Finished>().lastOrNull()
    return if (latest != null && latest.outcome.failures > 0) {
        TaskbarProgress(TaskbarState.ERROR, FULL_PERCENT)
    } else {
        TaskbarProgress(TaskbarState.NONE, 0)
    }
}

private fun runningProgress(running: JobState.Running): TaskbarProgress {
    val progress = running.progress ?: return TaskbarProgress(TaskbarState.INDETERMINATE, 0)
    return TaskbarProgress(TaskbarState.NORMAL, (progress.fraction * FULL_PERCENT).toInt())
}
