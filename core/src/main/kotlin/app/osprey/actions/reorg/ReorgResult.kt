package app.osprey.actions.reorg

/** One file [ReorgPlanner.apply] could not finish, and why. Its source is intact. */
data class ReorgFailure(val label: String, val reason: String)

/** What [ReorgPlanner.apply] did. [cancelled] is true when it stopped early at the caller's request. */
data class ReorgResult(
    val copied: Int,
    val moved: Int,
    val refiled: Int,
    val failures: List<ReorgFailure>,
    val cancelled: Boolean,
) {
    val done: Int get() = copied + moved + refiled
}
