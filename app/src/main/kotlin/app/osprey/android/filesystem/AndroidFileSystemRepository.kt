package app.osprey.android.filesystem

import android.content.ContentResolver
import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.osprey.filesystem.ChannelRange
import app.osprey.filesystem.ContentReplacer
import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTransfer
import app.osprey.filesystem.FileTimestamps
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant

/**
 * Real [FileSystemRepository] over the Storage Access Framework -- confirmed at interactive-UI
 * latency for hundreds of files by #9's spike (list 120 files: 133ms cold / 46ms warm; rename
 * 58ms; move 52ms). Every operation works off a [DocumentFile] reached from a persisted tree
 * [Uri] (the user-granted library folder, constructed by #14's folder picker via
 * `DocumentFile.fromTreeUri`), never a raw path.
 *
 * Created-time has no SAF equivalent at all (no such [DocumentsContract] column exists), and
 * [writeTimestamps] is a confirmed, documented no-op (#13): #9's finding #2 (a raw path recovers
 * for primary storage) resolves a real, *existing* `java.io.File`, but scoped storage (API 29+,
 * this app's targetSdk) blocks direct file writes to it regardless -- `canWrite()` is false and
 * `setLastModified()` returns false, confirmed on a real device/emulator, not assumed. There is
 * no other SAF-level API to set modified-time, so this never becomes a crash; it is always a
 * silent no-op, surfaced as user-facing copy in #14 rather than attempted here.
 */
class AndroidFileSystemRepository(
    private val context: Context,
    transfer: FileTransfer = AndroidFileTransfer(context),
    replacer: ContentReplacer = AndroidContentReplacer(context, transfer),
) : FileSystemRepository, FileTransfer by transfer, ContentReplacer by replacer {

    override fun list(folder: FileHandle): List<FileEntry> {
        val doc = folder.documentFile()
        if (!doc.isDirectory) return emptyList()
        return doc.listFiles().map { it.toEntry() }
    }

    override fun rename(entry: FileHandle, newName: String): FileHandle {
        val doc = entry.documentFile()
        if (doc.parentFile?.findFile(newName) != null) {
            throw FileAlreadyExistsException("$newName already exists")
        }
        check(doc.renameTo(newName)) { "rename failed: ${doc.name} -> $newName" }
        return AndroidFileHandle(doc)
    }

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        val doc = entry.documentFile()
        val destDoc = destFolder.documentFile()
        val targetName = newName ?: doc.name.orEmpty()
        if (destDoc.findFile(targetName) != null) {
            throw FileAlreadyExistsException("$targetName already exists in ${destDoc.name}")
        }
        // Rename before moving, while `doc` is still the tree-rooted DocumentFile reached via
        // list(): DocumentFile.fromSingleUri() (the shape moveDocument's result Uri would need)
        // returns a wrapper whose renameTo() unconditionally throws UnsupportedOperationException,
        // so a post-move rename on that result is never safe. Re-deriving the final handle via
        // destDoc.findFile() afterwards keeps it tree-rooted and avoids fromSingleUri entirely.
        if (newName != null && newName != doc.name) {
            check(doc.renameTo(newName)) { "pre-move rename failed: ${doc.name} -> $newName" }
        }
        val sourceParentUri = doc.parentFile?.uri ?: error("cannot move ${doc.name}: no parent")
        DocumentsContract.moveDocument(context.contentResolver, doc.uri, sourceParentUri, destDoc.uri)
            ?: error("move failed: ${doc.name} -> ${destDoc.name}")
        val moved = destDoc.findFile(targetName) ?: error("move produced no document")
        return AndroidFileHandle(moved)
    }

    override fun createFolder(parent: FileHandle, name: String): FileHandle {
        val parentDoc = parent.documentFile()
        val existing = parentDoc.findFile(name)
        if (existing != null) {
            if (existing.isDirectory) return AndroidFileHandle(existing)
            throw FileAlreadyExistsException("$name already exists as a file")
        }
        val created = parentDoc.createDirectory(name) ?: error("createFolder failed: $name")
        return AndroidFileHandle(created)
    }

    override fun deleteEmptyFolder(folder: FileHandle) {
        val doc = folder.documentFile()
        if (doc.listFiles().isNotEmpty()) {
            throw DirectoryNotEmptyException("folder is not empty: ${doc.name}")
        }
        check(doc.delete()) { "delete failed: ${doc.name}" }
    }

    override fun readBytes(entry: FileHandle): ByteArray = context.readAllBytes(entry)

    override fun readTimestamps(entry: FileHandle): FileTimestamps {
        val modified = entry.documentFile().lastModified().takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        return FileTimestamps(created = null, modified = modified)
    }

    // Params unused on purpose -- signature is the contract; see the class doc for why this is a no-op.
    @Suppress("UnusedParameter")
    override fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps) = Unit

    override fun readRange(entry: FileHandle, offset: Long, length: Int): ByteArray =
        context.contentResolver.openDescriptor(entry, "r").use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                ChannelRange.read(channel.size(), offset, length) { buffer, at -> channel.read(buffer, at) }
            }
        }

    // "rw", never "rwt": the file is not truncated. A provider that cannot seek (a cloud one) throws here, and the
    // save fails with its message.
    override fun overwriteAt(entry: FileHandle, offset: Long, bytes: ByteArray) {
        context.contentResolver.openDescriptor(entry, "rw").use { pfd ->
            FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                ChannelRange.write(channel.size(), offset, bytes) { buffer, at -> channel.write(buffer, at) }
                channel.force(true)
            }
        }
    }
}

private fun DocumentFile.toEntry(): FileEntry =
    FileEntry(AndroidFileHandle(this), name.orEmpty(), isDirectory, if (isDirectory) 0 else length())

internal fun FileHandle.documentFile(): DocumentFile = (this as AndroidFileHandle).documentFile

internal fun ContentResolver.openDescriptor(entry: FileHandle, mode: String): ParcelFileDescriptor {
    val uri = entry.documentFile().uri
    return openFileDescriptor(uri, mode) ?: error("cannot open $uri")
}

internal fun Context.readAllBytes(entry: FileHandle): ByteArray {
    val uri = entry.documentFile().uri
    return contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot open $uri")
}
