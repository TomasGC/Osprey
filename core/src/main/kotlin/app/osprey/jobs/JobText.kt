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
