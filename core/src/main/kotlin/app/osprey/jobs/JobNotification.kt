package app.osprey.jobs

private const val FULL_PERCENT = 100

/**
 * What a notification says about a job, decided here so every platform uses the same words and numbers.
 *
 * @property percent How far the job is, 0 to 100, or null when it shows no bar.
 * @property indeterminate True when the bar should move without a value (nothing reported yet, or still waiting).
 * @property stoppableJobId The job the Stop button stops, or null when there is nothing to stop.
 * @property failed True for a result with files that could not be handled.
 */
data class JobNotification(
    val title: String,
    val text: String,
    val percent: Int? = null,
    val indeterminate: Boolean = false,
    val stoppableJobId: Long? = null,
    val failed: Boolean = false,
)

/**
 * The one notification that stays up while jobs are waiting or running: the running job (or, when none runs,
 * the first waiting one) with how many wait behind it. Null once nothing is left to do.
 */
fun ongoingNotification(entries: List<JobEntry>): JobNotification? {
    val active = entries.filter { it.state !is JobState.Finished }
    val current = active.firstOrNull { it.state is JobState.Running } ?: active.firstOrNull() ?: return null
    val running = current.state as? JobState.Running
    val progress = running?.progress
    return JobNotification(
        title = jobTitle(current, waiting = active.size - 1),
        text = jobBody(current.state),
        percent = progress?.let { (it.fraction * FULL_PERCENT).toInt() },
        indeterminate = progress == null,
        stoppableJobId = current.id.takeUnless { running?.stopping == true },
    )
}

/** What to say once [entry] is over; null while it is not. */
fun resultNotification(entry: JobEntry): JobNotification? {
    val finished = entry.state as? JobState.Finished ?: return null
    val outcome = finished.outcome
    return JobNotification(title = entry.name, text = outcome.summary, failed = outcome.failures > 0)
}
/** What to tell the user when Android ended the background service because of its time limit. */
fun backgroundLimitNotification(): JobNotification = JobNotification(
    title = "Osprey stopped",
    text = "Android limits how long a job can run in the background. Open Osprey and scan again to continue.",
)
