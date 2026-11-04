package app.osprey.jobs

/** `3 of 10: a.jpg`: the file a job is starting, counted from one. */
fun progressText(progress: JobProgress): String = "${progress.done + 1} of ${progress.total}: ${progress.current}"

/** The job's name, with how many jobs are [waiting] behind it. */
fun jobTitle(entry: JobEntry, waiting: Int): String =
    if (waiting == 0) entry.name else "${entry.name} (+$waiting waiting)"

/** One line saying what [state] is doing now, for a notification or a banner. */
fun jobBody(state: JobState): String = when (state) {
    JobState.Queued -> "Waiting for the jobs before it"
    is JobState.Running -> runningBody(state)
    is JobState.Finished -> state.outcome.summary
}

private fun runningBody(state: JobState.Running): String = when {
    state.stopping -> "Stopping after this file..."
    state.progress == null -> "Working..."
    else -> progressText(state.progress)
}
/** [summary] as it should read once the job is over: a job that was asked to stop says so. */
fun withStoppedNote(summary: String, stopped: Boolean): String = if (stopped) "$summary (stopped)" else summary

/** [name] followed by the [scope] the action runs on (a year, say), or [name] alone when there is none. */
fun scopedName(name: String, scope: String?): String = if (scope.isNullOrBlank()) name else "$name $scope"

/** What an apply says about itself once over: [applied] done, [failed] failed, and a note if it was [stopped]. */
fun applyOutcome(name: String, applied: Int, failed: Int, stopped: Boolean): JobOutcome {
    val counts = "$name apply: $applied done" + if (failed > 0) ", $failed failed" else ""
    return JobOutcome(withStoppedNote(counts, stopped), stopped = stopped, failures = failed)
}

/** The text after an action's summary on its screen once an apply is over. */
fun appliedSuffix(applied: Int, failed: Int, stopped: Boolean): String =
    "  -- APPLIED ($applied)" + (if (failed > 0) ", failed $failed" else "") + if (stopped) " -- STOPPED" else ""
