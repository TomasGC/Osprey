package app.osprey.filesystem

import java.io.IOException

/** A file system whose first rename fails like a locked file, and later ones work. */
class FirstRenameFails(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    private var failed = false

    override fun rename(entry: FileHandle, newName: String): FileHandle {
        if (!failed) {
            failed = true
            throw IOException("locked")
        }
        return inner.rename(entry, newName)
    }
}
