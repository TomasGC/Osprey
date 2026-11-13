package app.osprey.jobs

/** Android drops a package's notification updates above about five a second; this keeps well under it. */
const val NOTIFICATION_INTERVAL_MS = 500L

/**
 * How long to wait before posting notifications, so that a burst of changes posts the first state and, one interval
 * later, the latest one. 0 posts at once. [lastPostMs] is when the last batch went out, null if none did; a clock
 * that went backwards waits one interval at most.
 */
fun notificationDelayMs(lastPostMs: Long?, nowMs: Long, intervalMs: Long = NOTIFICATION_INTERVAL_MS): Long {
    if (lastPostMs == null) return 0L
    return (intervalMs - (nowMs - lastPostMs)).coerceIn(0L, intervalMs)
}
