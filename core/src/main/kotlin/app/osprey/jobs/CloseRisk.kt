package app.osprey.jobs

/**
 * Whether closing the app now would cut a job that changes files: one that [JobEntry.writes] and is still
 * waiting or running. A finished job never counts, and a scan only reads.
 */
fun closeRisk(entries: List<JobEntry>): Boolean = entries.any { it.writes && it.state !is JobState.Finished }
