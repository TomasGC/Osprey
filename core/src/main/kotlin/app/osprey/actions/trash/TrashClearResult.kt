package app.osprey.actions.trash

/**
 * What a clear did: files [deleted], the [failures] (each file is still in the Trash), the empty
 * folders it removed ([prunedFolders]) and whether it stopped early at the caller's request.
 */
data class TrashClearResult(
    val deleted: Int,
    val failures: List<TrashFailure>,
    val prunedFolders: Int,
    val cancelled: Boolean,
)
