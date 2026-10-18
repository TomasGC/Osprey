package app.osprey.actions.flatten

/**
 * The result of [FlattenPlanner.plan]: nothing has moved yet.
 *
 * @property folders The nested subfolders found, deepest first — the order [FlattenPlanner.apply]
 * must remove them in, so a folder is always empty by the time its own removal is attempted.
 */
data class FlattenPlan(
    val moves: List<FlattenMove>,
    val folders: List<FlattenFolder>,
)
