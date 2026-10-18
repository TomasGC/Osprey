package app.osprey.actions.dedupe

/** The result of [DedupePlanner.plan]: nothing has moved yet. */
data class DedupePlan(val moves: List<DedupeMove>)
