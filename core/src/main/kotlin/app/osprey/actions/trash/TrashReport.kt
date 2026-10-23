package app.osprey.actions.trash

private const val STOPPED_LINE = "  STOPPED before the end, as asked"

/** What a clear did, then one line per file it could not delete (each is still in the Trash). */
fun clearResultLines(result: TrashClearResult): List<String> {
    val head = "  DELETED ${result.deleted}, failed ${result.failures.size}, " +
        "empty folders removed ${result.prunedFolders}"
    return listOf(head) + stoppedLine(result.cancelled) + failureLines(result.failures)
}

/** What a restore did ([conflicts] still wait for a decision), then one line per file it could not move. */
fun restoreResultLines(result: TrashRestoreResult, conflicts: Int): List<String> {
    val head = "  RESTORED ${result.restored}, failed ${result.failures.size}, to settle $conflicts, " +
        "empty folders removed ${result.prunedFolders}"
    return listOf(head) + stoppedLine(result.cancelled) + failureLines(result.failures)
}

private fun stoppedLine(cancelled: Boolean): List<String> = if (cancelled) listOf(STOPPED_LINE) else emptyList()

private fun failureLines(failures: List<TrashFailure>): List<String> =
    failures.map { "  FAILED ${it.label}: ${it.reason} (still in the Trash)" }
