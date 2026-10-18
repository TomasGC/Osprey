package app.osprey.actions.rename

/**
 * The result of [RenamePlanner.plan]: nothing has moved yet.
 *
 * @property skips Files whose cleaned name collides with an existing file or with another
 * rename already claimed earlier in this same plan — never silently overwritten, never crashed.
 */
data class RenamePlan(
    val renames: List<RenameAction>,
    val skips: List<RenameAction>,
)
