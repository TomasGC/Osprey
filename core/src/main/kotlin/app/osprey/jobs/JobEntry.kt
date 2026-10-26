package app.osprey.jobs

/** One job in the queue as a screen or a notification sees it: a snapshot, never changed in place. */
data class JobEntry(val id: Long, val name: String, val state: JobState)
