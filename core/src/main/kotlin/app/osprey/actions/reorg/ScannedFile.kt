package app.osprey.actions.reorg

import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/** An image or video found by [scanMedia], with the names of the folders between the scan root and it. */
internal data class ScannedFile(val entry: FileEntry, val folders: List<String>) {
    val relativePath: String get() = (folders + entry.name).joinToString("/")
}

private const val TRASH_FOLDER = "Trash"
private const val LEGACY_TRASH_PREFIX = "TODELETE"

/** The Trash and the legacy `TODELETE*` folders hold files set aside by Osprey: never a source. */
private fun isTrash(name: String) = name.equals(TRASH_FOLDER, ignoreCase = true) ||
    name.startsWith(LEGACY_TRASH_PREFIX, ignoreCase = true)

private fun isMedia(name: String): Boolean {
    val suffix = splitStemAndSuffix(name).second.lowercase()
    return suffix in imageExtensions || suffix in videoExtensions
}

/** Every image and video under [root], recursively, never entering a Trash folder. */
internal fun scanMedia(
    fs: FileSystemRepository,
    root: FileHandle,
    folders: List<String> = emptyList(),
): List<ScannedFile> =
    fs.list(root).flatMap { entry ->
        when {
            entry.isDirectory && isTrash(entry.name) -> emptyList()
            entry.isDirectory -> scanMedia(fs, entry.handle, folders + entry.name)
            isMedia(entry.name) -> listOf(ScannedFile(entry, folders))
            else -> emptyList()
        }
    }
