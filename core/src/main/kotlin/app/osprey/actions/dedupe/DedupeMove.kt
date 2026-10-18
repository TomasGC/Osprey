package app.osprey.actions.dedupe

import app.osprey.filesystem.FileHandle

/** One file that duplicates [keepName]'s content and will move into `TODELETE` as [destName]. */
data class DedupeMove(
    val source: FileHandle,
    val sourceName: String,
    val destName: String,
    val keepName: String,
)
