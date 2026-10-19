package app.osprey.actions.dates

import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime

/** One file whose capture datetime resolved, and where that resolution came from. */
data class DatesFix(
    val entry: FileHandle,
    val name: String,
    val dateTime: LocalDateTime,
    val source: MediaDateSource,
)
