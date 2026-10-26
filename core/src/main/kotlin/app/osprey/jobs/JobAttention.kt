package app.osprey.jobs

/** The jobs finished in [after] that were not in [before]: what a screen or a notification has not shown yet. */
fun newlyFinished(before: List<JobEntry>, after: List<JobEntry>): List<JobEntry> {
    val alreadyFinished = before.filter { it.state is JobState.Finished }.map { it.id }.toSet()
    return after.filter { it.state is JobState.Finished && it.id !in alreadyFinished }
}
