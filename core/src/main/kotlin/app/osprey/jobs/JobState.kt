package app.osprey.jobs

/** Where a job is in its life: waiting its turn, running, or over. */
sealed interface JobState {

    data object Queued : JobState

    /** [progress] is null until the job reports its first file; [stopping] once a stop was asked for. */
    data class Running(val progress: JobProgress? = null, val stopping: Boolean = false) : JobState

    data class Finished(val outcome: JobOutcome) : JobState
}
