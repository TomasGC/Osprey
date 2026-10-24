package app.osprey.actions.trash

/** One file a clear or restore could not finish, and why. */
data class TrashFailure(val label: String, val reason: String)
