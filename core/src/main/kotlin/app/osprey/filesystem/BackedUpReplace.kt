package app.osprey.filesystem

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

/**
 * Replaces a whole file through a write that is not safe by itself (on Android, one that truncates first), keeping a
 * verified copy beside it until the new content has been read back (#70). Whatever fails, the original content is
 * back in the file or the error says where the copy was kept.
 */
class BackedUpReplace(private val files: FileTransfer, private val readBytes: (FileHandle) -> ByteArray) {

    /**
     * Replaces [entry], named [name] and sitting in [folder], with [bytes] through [writeInPlace]. Throws when the
     * file could not take the new content; it then holds the original again.
     */
    fun replace(
        entry: FileHandle,
        folder: FileHandle,
        name: String,
        bytes: ByteArray,
        writeInPlace: (FileHandle, ByteArray) -> Unit,
    ) {
        val original = files.contentHash(entry)
        val backupName = "$name$BACKUP_MARKER${UUID.randomUUID()}"
        val backup = files.copyTo(entry, folder, backupName)
        if (files.contentHash(backup) != original) {
            discard(backup)
            throw IOException(BACKUP_DIFFERS)
        }
        val failure = failureReason { writeAndCheck(entry, bytes, writeInPlace) }
        if (failure == null) discard(backup) else putBack(entry, backup, backupName, original, failure, writeInPlace)
    }

    private fun writeAndCheck(entry: FileHandle, bytes: ByteArray, writeInPlace: (FileHandle, ByteArray) -> Unit) {
        writeInPlace(entry, bytes)
        if (files.contentHash(entry) != sha256Hex(ByteArrayInputStream(bytes))) throw IOException(DID_NOT_TAKE)
    }

    // Puts the original back and rethrows why the write failed; when that fails too the copy stays and is named.
    @Suppress("LongParameterList")
    private fun putBack(
        entry: FileHandle,
        backup: FileHandle,
        backupName: String,
        original: String,
        failure: String,
        writeInPlace: (FileHandle, ByteArray) -> Unit,
    ): Nothing {
        val problem = failureReason {
            writeInPlace(entry, readBytes(backup))
            if (files.contentHash(entry) != original) throw IOException(DID_NOT_TAKE)
        }
        if (problem == null) discard(backup)
        throw if (problem == null) IOException(failure) else IOException("$KEPT_AS $backupName", IOException(failure))
    }

    /** A copy that cannot be deleted is clutter, never a reason to fail a save. */
    private fun discard(backup: FileHandle) {
        failureReason { files.delete(backup) }
    }

    companion object {
        const val BACKUP_MARKER = ".osprey-backup-"
        const val BACKUP_DIFFERS = "The safety copy of the photo differs from it"
        const val DID_NOT_TAKE = "The photo did not take the change"
        const val KEPT_AS = "The photo could not be put back; its original is kept as"
    }
}
