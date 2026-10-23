package app.osprey.actions.trash

/** Progress of a clear or restore: [done] of [total] files handled, [current] being the one just started. */
data class TrashProgress(val done: Int, val total: Int, val current: String)

/** One file a clear or restore could not finish, and why. */
data class TrashFailure(val label: String, val reason: String)
