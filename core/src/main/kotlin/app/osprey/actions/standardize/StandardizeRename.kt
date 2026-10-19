package app.osprey.actions.standardize

import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDateSource
import java.time.LocalDateTime

/** One file to rename to its standardized `IMG_`/`VID_` name, and which date resolved it. */
data class StandardizeRename(
    val entry: FileHandle,
    val sourceName: String,
    val targetName: String,
    val dateTime: LocalDateTime,
    val source: MediaDateSource,
)
