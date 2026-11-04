package app.osprey.filesystem

import java.io.IOException

/** A file system whose first timestamp write fails like a locked file, and later ones work. */
class FirstTimestampWriteFails(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    private var failed = false

    override fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps) {
        if (!failed) {
            failed = true
            throw IOException("locked")
        }
        inner.writeTimestamps(entry, timestamps)
    }
}
