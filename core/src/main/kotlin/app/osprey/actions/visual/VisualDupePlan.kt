package app.osprey.actions.visual

/**
 * The result of [VisualDupePlanner.plan]: groups of visually near-identical files, largest file
 * first within each group. Unlike every other action, this is always review-before-apply, never
 * auto-applied — perceptual similarity can still be wrong (e.g. an annotated photo vs. its clean
 * original).
 */
data class VisualDupePlan(val groups: List<List<VisualDupeMember>>)
