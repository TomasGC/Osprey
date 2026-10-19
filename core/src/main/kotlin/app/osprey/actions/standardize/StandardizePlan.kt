package app.osprey.actions.standardize

import app.osprey.filesystem.FileEntry

/** The result of [StandardizePlanner.plan]: nothing has moved yet. */
data class StandardizePlan(
    val renames: List<StandardizeRename>,
    val unfixable: List<FileEntry>,
)
