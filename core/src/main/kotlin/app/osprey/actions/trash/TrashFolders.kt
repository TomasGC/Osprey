package app.osprey.actions.trash

import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val TRASH = "Trash"

/**
 * The trash folders of a library: `<libraryRoot>/Trash/<year>/<action>/`. `Trash` is not a
 * four-digit folder, so no action ever mistakes it for a year.
 */
class TrashFolders(private val fs: FileSystemRepository) {

    /** The folder for [action]'s trash of [location]'s year, created (with `Trash` and the year folder) if needed. */
    fun ensure(location: TrashLocation, action: TrashAction): FileHandle {
        val trash = fs.createFolder(location.libraryRoot, TRASH)
        val year = fs.createFolder(trash, location.year)
        return fs.createFolder(year, action.folderName)
    }

    /** The names already in that folder, or an empty set if it does not exist. Never creates anything. */
    fun existingNames(location: TrashLocation, action: TrashAction): Set<String> {
        val folder = subfolder(location.libraryRoot, TRASH)
            ?.let { subfolder(it, location.year) }
            ?.let { subfolder(it, action.folderName) }
            ?: return emptySet()
        return fs.list(folder).map { it.name }.toSet()
    }

    private fun subfolder(parent: FileHandle, name: String): FileHandle? =
        fs.list(parent).firstOrNull { it.isDirectory && it.name == name }?.handle
}

/**
 * [name] if it is free in [taken], otherwise the first `<stem>__dupN<suffix>` that is: a file in the
 * Trash is never overwritten.
 */
fun freeTrashName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val (stem, suffix) = splitStemAndSuffix(name)
    var n = 1
    while ("${stem}__dup$n$suffix" in taken) n++
    return "${stem}__dup$n$suffix"
}
