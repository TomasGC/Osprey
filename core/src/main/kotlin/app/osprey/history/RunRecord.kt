package app.osprey.history

import java.time.Instant

/** One run as the History screen shows it: a snapshot, never changed in place. */
data class RunRecord(
    val id: Long,
    val action: String,
    val state: RunState,
    val queuedAt: Instant,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val summary: String = "",
    val log: List<String> = emptyList(),
    val files: List<FileRecord> = emptyList(),
)
