package app.osprey.actions.trash

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val TRASH = "Trash"

/**
 * Finds things under `<libraryRoot>/Trash/<year>/<action>/`, for one clear or restore. A file is only
 * ever found by name inside those folders, which is what keeps a deletion inside the Trash. Folder
 * listings are read once and reused, so a thousand files cost one listing, not a thousand.
 */
internal class TrashLocator(private val fs: FileSystemRepository, private val libraryRoot: FileHandle) {

    private val listings = mutableMapOf<Pair<String, TrashAction>, Map<String, FileEntry>>()

    /** The file [entry] names, if it is still in its Trash folder. */
    fun find(entry: TrashEntry): FileEntry? {
        val key = entry.year to entry.action
        val listing = listings.getOrPut(key) {
            folder(entry.year, entry.action)
                ?.let { fs.list(it).filterNot { child -> child.isDirectory }.associateBy { child -> child.name } }
                .orEmpty()
        }
        return listing[entry.name]
    }

    /**
     * Removes the `Trash/[year]/[action]` folder if it is empty, then the year folder if that is empty
     * too. `Trash` itself is never removed. Returns how many folders went; a folder that is not empty
     * (a file was added, a delete failed) is simply kept.
     */
    fun prune(year: String, action: TrashAction): Int {
        val yearFolder = trashFolder()?.let { child(it, year) } ?: return 0
        var removed = 0
        child(yearFolder, action.folderName)?.let { if (removeIfEmpty(it)) removed++ }
        if (removeIfEmpty(yearFolder)) removed++
        return removed
    }

    private fun folder(year: String, action: TrashAction): FileHandle? =
        trashFolder()?.let { child(it, year) }?.let { child(it, action.folderName) }

    private fun trashFolder(): FileHandle? = child(libraryRoot, TRASH)

    private fun child(parent: FileHandle, name: String): FileHandle? =
        fs.list(parent).firstOrNull { it.isDirectory && it.name == name }?.handle

    private fun removeIfEmpty(folder: FileHandle): Boolean {
        if (fs.list(folder).isNotEmpty()) return false
        return try {
            fs.deleteEmptyFolder(folder)
            true
        } catch (_: DirectoryNotEmptyException) {
            false
        }
    }
}
