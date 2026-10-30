package app.osprey.viewer

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.filesystem.failureReason
import app.osprey.media.HeicEdit
import app.osprey.media.HeicTurn
import app.osprey.media.HeicTurnResult

/**
 * Saves a quarter turn into a HEIC (#71). Most photos are turned in place: [HeicTurn] names the few bytes to change
 * (the `irot` byte and the Exif orientation), they are written, read back and compared, and put back if they differ.
 * A photo with no `irot` gets one, which makes the file longer, so it is replaced as a whole
 * ([app.osprey.filesystem.ContentReplacer]) once its content has been checked. [freeMemory] says how many bytes of
 * heap are free: the file is read whole and a rebuild holds several copies of it.
 */
class HeicTurnSaver(private val files: FileSystemRepository, private val freeMemory: () -> Long) {

    fun save(entry: FileHandle, quarterTurns: Int): TurnSaveResult {
        val file = files.readBytes(entry)
        if (freeMemory() < file.size.toLong() * COPIES) return TurnSaveResult.Refused(TOO_BIG)
        return when (val turned = HeicTurn.turn(file, quarterTurns)) {
            HeicTurnResult.Unchanged -> TurnSaveResult.Saved
            is HeicTurnResult.Refused -> TurnSaveResult.Refused(turned.reason)
            is HeicTurnResult.Patched -> keepingTimes(entry, withNote(turned.note)) { patch(entry, file, turned.edits) }
            is HeicTurnResult.Rebuilt -> keepingTimes(entry, withNote(turned.note)) {
                files.replaceContent(entry, turned.bytes)
                null
            }
        }
    }

    private fun withNote(note: String?): TurnSaveResult =
        if (note == null) TurnSaveResult.Saved else TurnSaveResult.SavedWithNote(note)

    /** Runs [write], which returns why it failed or null, and puts the modified time back; a good save is [saved]. */
    private fun keepingTimes(entry: FileHandle, saved: TurnSaveResult, write: () -> String?): TurnSaveResult {
        val before = files.readTimestamps(entry)
        val failure = write()
        // Best effort: a modified time that cannot be put back does not undo a good save.
        failureReason { files.writeTimestamps(entry, FileTimestamps(modified = before.modified)) }
        return if (failure == null) saved else TurnSaveResult.Failed(failure)
    }

    /** Writes every edit, reads each back and compares; on any mismatch all the old bytes go back. */
    private fun patch(entry: FileHandle, file: ByteArray, edits: List<HeicEdit>): String? {
        val written = failureReason {
            edits.forEach { files.overwriteAt(entry, it.offset.toLong(), it.bytes) }
        }
        val verified = written == null &&
            edits.all { files.readRange(entry, it.offset.toLong(), it.bytes.size).contentEquals(it.bytes) }
        if (verified) return null
        for (edit in edits) {
            val old = file.copyOfRange(edit.offset, edit.offset + edit.bytes.size)
            files.overwriteAt(entry, edit.offset.toLong(), old)
        }
        return written ?: DID_NOT_TAKE
    }

    companion object {
        const val TOO_BIG = "This HEIC is too large to turn with the memory that is free"
        private const val DID_NOT_TAKE = "The photo did not take the change"

        // The file, a rebuilt copy, the two copies the check clears the orientation in, and room to spare.
        private const val COPIES = 5
    }
}
