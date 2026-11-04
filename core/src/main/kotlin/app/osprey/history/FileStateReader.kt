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
