package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val TRASH = "Trash"

/**
 * Reads `<libraryRoot>/Trash/<year>/<action>/` and nothing else: a folder that is not a year, an
 * action folder Osprey does not know, a loose file or a subfolder is never listed, so it can never
 * be cleared or restored.
 */
class TrashScanner(private val fs: FileSystemRepository) {

    /** The files currently in the Trash of [libraryRoot]; nothing on disk changes. */
    fun scan(libraryRoot: FileHandle): TrashContents {
        val trash = fs.list(libraryRoot).firstOrNull { it.isDirectory && it.name == TRASH }
            ?: return TrashContents(emptyList())
        val entries = fs.list(trash.handle)
            .filter { it.isDirectory && isYear(it.name) }
            .flatMap { year -> entriesOfYear(year.name, year.handle) }
        return TrashContents(entries)
    }

    private fun entriesOfYear(year: String, folder: FileHandle): List<TrashEntry> {
        val actionFolders = fs.list(folder).filter { it.isDirectory }.associateBy { it.name }
        return TrashAction.entries.flatMap { action ->
            val actionFolder = actionFolders[action.folderName]
            actionFolder?.let { found ->
                fs.list(found.handle).filterNot { it.isDirectory }
                    .map { TrashEntry(year, action, it.handle, it.name, it.sizeBytes) }
            }.orEmpty()
        }
    }

    private fun isYear(name: String): Boolean = name.isNotEmpty() && name.all(Char::isDigit)
}
