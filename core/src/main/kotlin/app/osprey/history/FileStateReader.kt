package app.osprey.history

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.filesystem.failureReason

/** [handle]'s state now. Dates the file system cannot give are left out: they are never a reason to fail the change. */
fun FileSystemRepository.stateOf(handle: FileHandle, name: String, sizeBytes: Long): FileState {
    var stamps = FileTimestamps()
    failureReason { stamps = readTimestamps(handle) }
    return FileState(sizeBytes, stamps.created, stamps.modified, name = name)
}

/**
 * [handle]'s state now with the SHA-256 of its content, read in chunks, or null when the file system cannot give it
 * (#79): a history that cannot read a file must never be the reason a save fails.
 */
fun FileSystemRepository.contentStateOf(handle: FileHandle, name: String): FileState? {
    var state: FileState? = null
    failureReason { state = stateOf(handle, name, size(handle)).copy(hash = contentHash(handle)) }
    return state
}
