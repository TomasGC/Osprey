package app.osprey.actions.trash

import app.osprey.filesystem.failureReason

/** Runs [action] for the file called [label]; what a file system can throw at it becomes a [TrashFailure]. */
internal fun attempt(label: String, action: () -> Unit): TrashFailure? =
    failureReason(action)?.let { TrashFailure(label, it) }
