package app.osprey.actions.reorg

/** Progress of [ReorgPlanner.apply]: [done] of [total] files handled, [current] being the one just started. */
data class ReorgProgress(val done: Int, val total: Int, val current: String)
