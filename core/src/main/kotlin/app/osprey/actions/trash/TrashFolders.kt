package app.osprey.actions.trash

import app.osprey.actions.TakenNames
import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.childFolder

private const val TRASH = "Trash"

/**
 * The trash folders of a library: `<libraryRoot>/Trash/<year>/<action>/`. `Trash` is not a
 * four-digit folder, so no action ever mistakes it for a year.
 */
class TrashFolders(private val fs: FileSystemRepository) {

    /** The folder for [action]'s trash of [location]'s year, created (with `Trash` and the year folder) if needed. */
    fun ensure(location: TrashLocation, action: TrashAction): FileHandle {
        val trash = existingOrNew(location.libraryRoot, TRASH)
        val year = existingOrNew(trash, location.year)
        return existingOrNew(year, action.folderName)
    }

    /** The names already in that folder, or an empty set if it does not exist. Never creates anything. */
    fun existingNames(location: TrashLocation, action: TrashAction): Set<String> {
        val folder = fs.childFolder(location.libraryRoot, TRASH)
            ?.let { fs.childFolder(it, location.year) }
            ?.let { fs.childFolder(it, action.folderName) }
            ?: return emptySet()
        return fs.list(folder).map { it.name }.toSet()
    }

    private fun existingOrNew(parent: FileHandle, name: String): FileHandle =
        fs.childFolder(parent, name) ?: fs.createFolder(parent, name)
}

/**
 * [name] if it is free in [taken], otherwise the first `<stem>__dupN<suffix>` that is: a file in the
 * Trash is never overwritten. A name that differs only by case is taken (#132).
 */
fun freeTrashName(name: String, taken: Set<String>): String {
    val used = TakenNames(taken)
    if (name !in used) return name
    val (stem, suffix) = splitStemAndSuffix(name)
    var n = 1
    while ("${stem}__dup$n$suffix" in used) n++
    return "${stem}__dup$n$suffix"
}
