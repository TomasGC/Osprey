package app.osprey.filesystem

/**
 * One entry returned by [FileSystemRepository.list]: either a file or a subfolder.
 *
 * @property handle Opaque reference to this entry, usable in later [FileSystemRepository] calls.
 * @property name The entry's own name (not a path) within its parent folder.
 * @property isDirectory Whether this entry is a subfolder rather than a file.
 * @property sizeBytes The file's size in bytes; 0 for a directory.
 */
data class FileEntry(
    val handle: FileHandle,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)
