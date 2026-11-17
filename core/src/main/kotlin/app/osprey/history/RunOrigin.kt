package app.osprey.history

/**
 * Where a run came from (#83), so its page can offer to retry its failed files: the [kind] of action, the [library]
 * it ran on, the [scope] (a year) if it ran on one, and the run it retries, if it is a retry.
 */
data class RunOrigin(
    val kind: String,
    val library: String,
    val scope: String? = null,
    val retryOf: Long? = null,
)
