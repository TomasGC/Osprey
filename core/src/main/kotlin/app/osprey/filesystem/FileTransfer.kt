package app.osprey.filesystem

/**
 * The cross-folder half of a [FileSystemRepository], used by Reorg to gather files from folders
 * that may sit on another provider (on Android, another SAF provider, where a move is not possible).
 */
interface FileTransfer {

    /**
     * Copies [entry] into [destFolder] as [newName] (default: its own name) and returns the copy.
     * Never overwrites: throws [FileAlreadyExistsException] if the name is taken. The copy appears
     * under its final name only once complete, so an interrupted copy never leaves a truncated
     * file that looks like the real one.
     */
    fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String? = null): FileHandle

    /** Deletes the file [entry]. Used only after a copy has been verified, or to discard a failed copy. */
    fun delete(entry: FileHandle)

    /** SHA-256 of [entry]'s content as lowercase hex, read in chunks so a large video is never fully in memory. */
    fun contentHash(entry: FileHandle): String
}
