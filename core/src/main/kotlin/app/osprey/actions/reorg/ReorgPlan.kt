package app.osprey.actions.reorg

/**
 * The result of [ReorgPlanner.plan]: nothing has moved yet.
 *
 * @property items Files to copy, move or refile.
 * @property alreadyInOutput Labels of input files skipped because their content is already in the output
 * (or planned from another input).
 * @property undated Labels of files with no resolvable capture date; they stay where they are.
 * @property unreachable Labels of input folders that could not be read; the rest still ran.
 */
data class ReorgPlan(
    val items: List<ReorgItem>,
    val alreadyInOutput: List<String>,
    val undated: List<String>,
    val unreachable: List<String>,
)
