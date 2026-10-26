package app.osprey.jobs

/**
 * One job in the queue as a screen or a notification sees it: a snapshot, never changed in place.
 * [writes] is false for a job that only reads (a scan), so closing the app during it loses nothing.
 */
data class JobEntry(val id: Long, val name: String, val state: JobState, val writes: Boolean = true)
