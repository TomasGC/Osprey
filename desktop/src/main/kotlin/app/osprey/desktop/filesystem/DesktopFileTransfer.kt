package app.osprey.desktop.filesystem

import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileTransfer
import app.osprey.filesystem.sha256Hex
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.FileAlreadyExistsException as NioFileAlreadyExistsException

private const val PARTIAL_SUFFIX = ".osprey-part"

/** [FileTransfer] over `java.nio.file`. */
class DesktopFileTransfer : FileTransfer {

    override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        val source = (entry as PathHandle).path
        val target = (destFolder as PathHandle).path.resolve(newName ?: source.fileName.toString())
        if (Files.exists(target)) throw FileAlreadyExistsException("$target already exists")
        // Copy under a temporary name and rename at the end: the final name only ever holds a complete file.
        val partial = target.resolveSibling(target.fileName.toString() + PARTIAL_SUFFIX)
        try {
            Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING)
            return PathHandle(Files.move(partial, target))
        } catch (e: NioFileAlreadyExistsException) {
            throw FileAlreadyExistsException("$target already exists: ${e.message}", e)
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    override fun delete(entry: FileHandle) {
        Files.delete((entry as PathHandle).path)
    }

    override fun contentHash(entry: FileHandle): String =
        Files.newInputStream((entry as PathHandle).path).use { sha256Hex(it) }
}
