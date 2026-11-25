package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.childFolder

private const val TRASH = "Trash"

/**
 * Reads `<libraryRoot>/Trash/<year>/<action>/` and nothing else: a folder that is not a year, an
 * action folder Osprey does not know, a loose file or a subfolder is never listed, so it can never
 * be cleared or restored.
 */
class TrashScanner(private val fs: FileSystemRepository) {

    /** The files currently in the Trash of [libraryRoot]; nothing on disk changes. */
    fun scan(libraryRoot: FileHandle): TrashContents {
        val trash = fs.childFolder(libraryRoot, TRASH) ?: return TrashContents(emptyList())
        val entries = fs.list(trash)
            .filter { it.isDirectory && isYear(it.name) }
            .flatMap { year -> entriesOfYear(year.name, year.handle) }
        return TrashContents(entries)
    }

    private fun entriesOfYear(year: String, folder: FileHandle): List<TrashEntry> {
        return TrashAction.entries.flatMap { action ->
            fs.childFolder(folder, action.folderName)?.let { found ->
                fs.list(found).filterNot { it.isDirectory }
                    .map { TrashEntry(year, action, it.handle, it.name, it.sizeBytes) }
            }.orEmpty()
        }
    }

    private fun isYear(name: String): Boolean = name.isNotEmpty() && name.all(Char::isDigit)
}
