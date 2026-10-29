package app.osprey.desktop.filesystem

import app.osprey.filesystem.ChannelRange
import app.osprey.filesystem.ContentReplacer
import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTransfer
import app.osprey.filesystem.FileTimestamps
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.DirectoryNotEmptyException as NioDirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException as NioFileAlreadyExistsException

/**
 * Real [FileSystemRepository] for plain Windows/Unix paths, via `java.nio.file` — the same power
 * `pathlib` gave the POC, nothing lost. Created-time is set natively on Windows through
 * [BasicFileAttributeView], no `pywin32`/JNI workaround needed this time; on Unix it's a no-op,
 * same portability story the POC already had.
 */
class DesktopFileSystemRepository(
    transfer: FileTransfer = DesktopFileTransfer(),
    replacer: ContentReplacer = DesktopContentReplacer(),
) : FileSystemRepository, FileTransfer by transfer, ContentReplacer by replacer {

    override fun list(folder: FileHandle): List<FileEntry> {
        val path = folder.path()
        if (!Files.isDirectory(path)) return emptyList()
        return Files.newDirectoryStream(path).use { stream -> stream.map { it.toEntry() } }
    }

    override fun rename(entry: FileHandle, newName: String): FileHandle =
        moveWithoutOverwrite(entry.path(), entry.path().resolveSibling(newName))

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        val targetName = newName ?: entry.path().fileName.toString()
        return moveWithoutOverwrite(entry.path(), destFolder.path().resolve(targetName))
    }

    override fun createFolder(parent: FileHandle, name: String): FileHandle {
        val path = parent.path().resolve(name)
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) return PathHandle(path)
            throw FileAlreadyExistsException("$name already exists as a file")
        }
        return PathHandle(Files.createDirectory(path))
    }

    override fun deleteEmptyFolder(folder: FileHandle) {
        try {
            Files.delete(folder.path())
        } catch (e: NioDirectoryNotEmptyException) {
            throw DirectoryNotEmptyException("folder is not empty: ${e.message}", e)
        }
    }

    override fun readBytes(entry: FileHandle): ByteArray = Files.readAllBytes(entry.path())

    override fun readRange(entry: FileHandle, offset: Long, length: Int): ByteArray =
        FileChannel.open(entry.path(), StandardOpenOption.READ).use { channel ->
            ChannelRange.read(channel.size(), offset, length) { buffer, at -> channel.read(buffer, at) }
        }

    // WRITE only: no truncate and no create. ChannelRange refuses a range past the end, so the file never grows.
    override fun overwriteAt(entry: FileHandle, offset: Long, bytes: ByteArray) {
        FileChannel.open(entry.path(), StandardOpenOption.WRITE).use { channel ->
            ChannelRange.write(channel.size(), offset, bytes) { buffer, at -> channel.write(buffer, at) }
            channel.force(true)
        }
    }

    override fun readTimestamps(entry: FileHandle): FileTimestamps {
        val attributes = Files.readAttributes(entry.path(), BasicFileAttributes::class.java)
        return FileTimestamps(
            created = attributes.creationTime().toInstant(),
            modified = attributes.lastModifiedTime().toInstant(),
        )
    }

    override fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps) {
        val view = Files.getFileAttributeView(entry.path(), BasicFileAttributeView::class.java)
        view.setTimes(
            timestamps.modified?.let { FileTime.from(it) },
            null,
            timestamps.created?.let { FileTime.from(it) },
        )
    }
}

private fun moveWithoutOverwrite(source: Path, target: Path): FileHandle = try {
    PathHandle(Files.move(source, target))
} catch (e: NioFileAlreadyExistsException) {
    throw FileAlreadyExistsException("$target already exists: ${e.message}", e)
}

private fun Path.toEntry(): FileEntry {
    val isDirectory = Files.isDirectory(this)
    val sizeBytes = if (isDirectory) 0L else Files.size(this)
    return FileEntry(PathHandle(this), fileName.toString(), isDirectory, sizeBytes)
}

private fun FileHandle.path(): Path = (this as PathHandle).path
