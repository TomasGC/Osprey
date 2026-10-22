package app.osprey.actions.visual

/** The outcome of [moveUncheckedToTrash]: matches the POC's `move_dups` return shape. */
data class MoveDupsResult(val moved: List<String>, val skipped: List<String>)
