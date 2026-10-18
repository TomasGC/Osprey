package app.osprey.filesystem

import java.time.Instant

/**
 * An entry's created/modified timestamps. Either field may be absent: a platform that cannot
 * expose or set a created time (the POC's non-Windows fallback) simply leaves it null.
 */
data class FileTimestamps(
    val created: Instant? = null,
    val modified: Instant? = null,
)
