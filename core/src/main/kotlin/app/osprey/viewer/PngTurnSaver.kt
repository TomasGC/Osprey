package app.osprey.viewer

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.filesystem.failureReason
import app.osprey.media.PngTurn
import app.osprey.media.PngTurnResult

/**
 * Saves a quarter turn into a PNG (#70). A PNG has no orientation tag every viewer honours, so the pixels are turned
 * and the file is replaced as a whole ([app.osprey.filesystem.ContentReplacer]); the handle stays valid.
 * [freeMemory] says how many bytes of heap are free: a picture whose copies would not fit is refused, because running
 * out of memory ends the app on Android.
 */
class PngTurnSaver(private val files: FileSystemRepository, private val freeMemory: () -> Long) {

    fun save(entry: FileHandle, quarterTurns: Int): TurnSaveResult {
        val file = files.readBytes(entry)
        return when (val turned = PngTurn.turn(file, quarterTurns, rawBudget())) {
            PngTurnResult.Unchanged -> TurnSaveResult.Saved
            is PngTurnResult.Refused -> TurnSaveResult.Refused(turned.reason)
            is PngTurnResult.Turned -> replace(entry, turned.bytes)
        }
    }

    // Asked once the file is read, so its bytes already count as used. The unfiltered picture and the turned one are
    // held together, with the compressed result coming on top of one of them: three copies of the scanlines.
    private fun rawBudget(): Int =
        (freeMemory() / RAW_COPIES).coerceIn(0L, PngTurn.MAX_RAW_BYTES.toLong()).toInt()

    private fun replace(entry: FileHandle, bytes: ByteArray): TurnSaveResult {
        val before = files.readTimestamps(entry)
        files.replaceContent(entry, bytes)
        // Best effort, like the JPEG save: a time that cannot be put back does not undo a good save.
        failureReason {
            files.writeTimestamps(entry, FileTimestamps(modified = before.modified))
        }
        return TurnSaveResult.Saved
    }

    private companion object {
        const val RAW_COPIES = 3
    }
}
