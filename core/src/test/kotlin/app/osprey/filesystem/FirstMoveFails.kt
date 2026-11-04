package app.osprey.filesystem

import java.io.IOException

/** A file system whose first move fails like a locked file, and later ones work. */
class FirstMoveFails(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    private var failed = false

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        if (!failed) {
            failed = true
            throw IOException("locked")
        }
        return inner.move(entry, destFolder, newName)
    }
}
