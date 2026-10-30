package app.osprey.filesystem

/**
 * A [FileSystemRepository] over [inner] that throws what a hook returns, so a test can make one
 * operation fail while everything else behaves. A hook returning null lets the call through.
 */
class FaultyFileSystemRepository(private val inner: FileSystemRepository) : FileSystemRepository by inner {

    var failCopy: (entry: FileHandle) -> Throwable? = { null }
    var failMove: (entry: FileHandle) -> Throwable? = { null }
    var failDeleteEmptyFolder: (folder: FileHandle) -> Throwable? = { null }

    var failDelete: (entry: FileHandle) -> Throwable? = { null }
    var failReplace: (entry: FileHandle) -> Throwable? = { null }
    var failOverwrite: (entry: FileHandle) -> Throwable? = { null }
    var failWriteTimestamps: (entry: FileHandle) -> Throwable? = { null }

    override fun overwriteAt(entry: FileHandle, offset: Long, bytes: ByteArray) {
        failOverwrite(entry)?.let { throw it }
        inner.overwriteAt(entry, offset, bytes)
    }

    override fun replaceContent(entry: FileHandle, bytes: ByteArray) {
        failReplace(entry)?.let { throw it }
        inner.replaceContent(entry, bytes)
    }

    override fun delete(entry: FileHandle) {
        failDelete(entry)?.let { throw it }
        inner.delete(entry)
    }

    override fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps) {
        failWriteTimestamps(entry)?.let { throw it }
        inner.writeTimestamps(entry, timestamps)
    }

    override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        failCopy(entry)?.let { throw it }
        return inner.copyTo(entry, destFolder, newName)
    }

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        failMove(entry)?.let { throw it }
        return inner.move(entry, destFolder, newName)
    }

    override fun deleteEmptyFolder(folder: FileHandle) {
        failDeleteEmptyFolder(folder)?.let { throw it }
        inner.deleteEmptyFolder(folder)
    }
}
