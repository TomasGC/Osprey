package app.osprey.history

/** The runs of one [state], as the History screen groups them. */
data class RunSection(val state: RunState, val runs: List<RunRecord>) {

    val title: String
        get() = when (state) {
            RunState.Running -> "Running"
            RunState.Queued -> "Waiting"
            RunState.Failed -> "Failed"
            RunState.Stopped -> "Stopped"
            RunState.Interrupted -> "Interrupted"
            RunState.Done -> "Done"
        }
}

/**
 * [runs] grouped by state in this order: Running, Waiting, Failed, Stopped, Interrupted, Done. Empty groups are left
 * out and the runs keep the order they were given in (newest first).
 */
fun runSections(runs: List<RunRecord>): List<RunSection> =
    SECTION_ORDER.map { state -> RunSection(state, runs.filter { it.state == state }) }.filter { it.runs.isNotEmpty() }

private val SECTION_ORDER = listOf(
    RunState.Running,
    RunState.Queued,
    RunState.Failed,
    RunState.Stopped,
    RunState.Interrupted,
    RunState.Done,
)
