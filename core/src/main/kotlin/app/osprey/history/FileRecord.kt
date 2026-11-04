package app.osprey.history

import java.time.Instant

enum class FileResult { Done, Failed, Skipped }

/**
 * What a file looked like at one moment. [hash] is filled only where the action reads the bytes anyway; [name] where
 * the action can change it.
 */
data class FileState(
    val size: Long,
    val created: Instant? = null,
    val modified: Instant? = null,
    val hash: String? = null,
    val name: String? = null,
)

/** One file a run handled: where, how it went, and, for actions that change metadata, [before] and [after]. */
data class FileRecord(
    val path: String,
    val result: FileResult,
    val message: String = "",
    val before: FileState? = null,
    val after: FileState? = null,
)
