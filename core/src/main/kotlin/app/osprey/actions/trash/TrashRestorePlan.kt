package app.osprey.actions.trash

import app.osprey.filesystem.FileEntry

/** A Trash file whose name is already taken in `<library>/<year>/`: both sides, to compare. */
data class RestoreConflict(val entry: TrashEntry, val existing: FileEntry)

/** The entries a restore can do at once ([direct]) and the ones that need a decision ([conflicts]). */
data class RestorePlan(val direct: List<TrashEntry>, val conflicts: List<RestoreConflict>)

/** What to keep when a restored file meets a library file of the same name. */
enum class ConflictChoice {
    /** The Trash's file takes the name; the library's file goes to `Trash/<year>/Replaced`. */
    TRASH_COPY,

    /** The library's file stays; the Trash's file stays in the Trash. */
    LIBRARY_COPY,

    /** Both are kept: the Trash's file is restored as `name__dupN`. */
    BOTH,
}

/**
 * What a restore did: files [restored], the [failures] (each file is still in the Trash), the empty
 * folders it removed ([prunedFolders]) and whether it stopped early.
 */
data class TrashRestoreResult(
    val restored: Int,
    val failures: List<TrashFailure>,
    val prunedFolders: Int,
    val cancelled: Boolean,
)
