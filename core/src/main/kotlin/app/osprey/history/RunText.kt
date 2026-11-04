package app.osprey.history

import app.osprey.filesystem.formatSize
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SECONDS_IN_MINUTE = 60L
private const val SECONDS_IN_HOUR = 3600L
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** One line under a run's name: what it is doing, or how it ended. */
fun runSubtitle(run: RunRecord): String = when (run.state) {
    RunState.Queued -> "Waiting for the jobs before it"
    RunState.Running -> "Working..."
    RunState.Interrupted -> "Interrupted: the app closed before it finished"
    RunState.Done, RunState.Failed, RunState.Stopped -> run.summary
}

/** How long the run took, or null until it has both started and ended. */
fun runDurationText(run: RunRecord): String? {
    val start = run.startedAt ?: return null
    val end = run.endedAt ?: return null
    return durationText(Duration.between(start, end).seconds)
}

/** When the run began, in [zone]: its start, or the moment it was queued while it has not started. */
fun runWhenText(run: RunRecord, zone: ZoneId): String = dateText(run.startedAt ?: run.queuedAt, zone)

private fun durationText(seconds: Long): String = when {
    seconds < 1 -> "under 1 s"
    seconds < SECONDS_IN_MINUTE -> "$seconds s"
    seconds < SECONDS_IN_HOUR -> units(seconds / SECONDS_IN_MINUTE to "min", seconds % SECONDS_IN_MINUTE to "s")
    else -> units(seconds / SECONDS_IN_HOUR to "h", seconds % SECONDS_IN_HOUR / SECONDS_IN_MINUTE to "min")
}

private fun units(vararg parts: Pair<Long, String>): String =
    parts.filter { it.first > 0 }.joinToString(" ") { "${it.first} ${it.second}" }

/** `3 files: 2 done, 1 failed`, or that no file was recorded. */
fun runCounts(run: RunRecord): String {
    if (run.files.isEmpty()) return "No files recorded"
    val results = listOf(FileResult.Done to "done", FileResult.Failed to "failed", FileResult.Skipped to "skipped")
        .map { (result, word) -> run.files.count { it.result == result } to word }
        .filter { it.first > 0 }
        .joinToString(", ") { "${it.first} ${it.second}" }
    val total = run.files.size
    return "$total ${if (total == 1) "file" else "files"}: $results"
}

/** One line for a file of a run: its path, then how it went. */
fun fileLine(record: FileRecord): String {
    val verdict = when (record.result) {
        FileResult.Done -> null
        FileResult.Failed -> "FAILED"
        FileResult.Skipped -> "skipped"
    }
    val text = listOfNotNull(verdict, record.message.takeIf { it.isNotBlank() }).joinToString(": ")
    return if (text.isEmpty()) record.path else "${record.path}: $text"
}

/** The `before:` and `after:` lines of a file that was changed, with dates in [zone]; none when it has neither. */
fun fileChangeLines(record: FileRecord, zone: ZoneId): List<String> = listOfNotNull(
    record.before?.let { "before: ${stateText(it, zone)}" },
    record.after?.let { "after: ${stateText(it, zone)}" },
)

private fun stateText(state: FileState, zone: ZoneId): String = listOfNotNull(
    state.name,
    formatSize(state.size),
    state.created?.let { "created ${dateText(it, zone)}" },
    state.modified?.let { "modified ${dateText(it, zone)}" },
    state.hash?.let { "hash $it" },
).joinToString(", ")

private fun dateText(at: Instant, zone: ZoneId): String = DATE_FORMAT.format(at.atZone(zone))
