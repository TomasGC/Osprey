package app.osprey.jobs

/** Progress of a job: [done] of [total] files handled, [current] being the one just started. */
data class JobProgress(val done: Int, val total: Int, val current: String) {

    /** The share of files handled so far, 0 to 1; 0 while the total is unknown. */
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}
