package app.osprey.history

import java.time.Instant

/** One line of a run file (#79): what happened to a run, in the order it happened. */
sealed interface RunLine {
    data class Header(val id: Long, val action: String, val at: Instant) : RunLine

    data class Started(val at: Instant) : RunLine

    data class Logged(val line: String) : RunLine

    data class Filed(val record: FileRecord) : RunLine

    data class Ended(val state: RunState, val summary: String, val at: Instant) : RunLine
}
