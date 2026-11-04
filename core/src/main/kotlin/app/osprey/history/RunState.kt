package app.osprey.history

/** Where a recorded run is: waiting, running, or over (and how). Interrupted: the app ended while it was active. */
enum class RunState {
    Queued,
    Running,
    Done,
    Failed,
    Stopped,
    Interrupted,
    ;

    /** True while the run has not ended; such a run is never dropped from the history. */
    val isActive: Boolean get() = this == Queued || this == Running
}
