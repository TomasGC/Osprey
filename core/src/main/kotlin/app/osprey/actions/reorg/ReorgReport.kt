package app.osprey.actions.reorg

private const val STOPPED_LINE = "  STOPPED before the end, as asked; run Scan again to continue"

/** The one-line summary of a scan, shown above the log on both platforms. */
fun reorgSummary(plan: ReorgPlan): String {
    val perOperation = ReorgOperation.entries.joinToString("  ") { operation ->
        "${operation.name.lowercase()}=${plan.items.count { it.operation == operation }}"
    }
    return "count=${plan.items.size}  $perOperation  already_in_output=${plan.alreadyInOutput.size}  " +
        "undated=${plan.undated.size}  unreachable=${plan.unreachable.size}"
}

/** What a run did, then one line per file it could not finish (each of those still has its source). */
fun reorgResultLines(result: ReorgResult): List<String> {
    val head = "  APPLIED copied=${result.copied} moved=${result.moved} refiled=${result.refiled} " +
        "failed=${result.failures.size}"
    val stopped = if (result.cancelled) listOf(STOPPED_LINE) else emptyList()
    return listOf(head) + stopped + result.failures.map { "  FAILED ${it.label}: ${it.reason} (source kept)" }
}
