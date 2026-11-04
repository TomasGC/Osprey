package app.osprey.filesystem

import java.io.IOException

/** A file system whose every move fails like a locked file. */
class MoveFails(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle =
        throw IOException("locked")
}
