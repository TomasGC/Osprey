package app.osprey.actions.reorg

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Copies [source] into [destFolder] as [destName], checks the copy's size against [sizeBytes] and its content against
 * the source, and only then deletes the source; a bad copy is discarded and the source stays. Safe across providers,
 * where a plain move is not possible.
 */
internal fun FileSystemRepository.moveVerified(
    source: FileHandle,
    sizeBytes: Long,
    destFolder: FileHandle,
    destName: String,
) {
    val copy = copyTo(source, destFolder, destName)
    val copySize = list(destFolder).firstOrNull { it.name == destName }?.sizeBytes
    if (copySize != sizeBytes || contentHash(copy) != contentHash(source)) {
        delete(copy)
        throw VerificationException("the copy differs from the source")
    }
    delete(source)
}
