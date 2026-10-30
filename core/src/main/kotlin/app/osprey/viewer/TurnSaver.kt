package app.osprey.viewer

import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.filesystem.failureReason
import app.osprey.media.JpegOrientation
import app.osprey.actions.heicExtensions

private const val KIBIBYTE = 1024

/**
 * Saves a quarter turn into a JPEG by changing its EXIF orientation value in place (#61): one byte of the file, found
 * in the first [HEADER_LIMIT] bytes, so nothing else is rewritten and no pixel, metadata field or trailer can be lost.
 * A PNG is turned in its pixels and the file replaced as a whole ([PngTurnSaver], #70). A HEIC keeps its rotation
 * in the container, so its `irot` and Exif orientation are changed ([HeicTurnSaver], #71).
 */
class TurnSaver(
    private val files: FileSystemRepository,
    freeMemory: () -> Long = ::freeHeapBytes,
) {

    private val png = PngTurnSaver(files, freeMemory)
    private val heic = HeicTurnSaver(files, freeMemory)

    fun save(entry: FileHandle, name: String, quarterTurns: Int): TurnSaveResult {
        if (!canSave(name)) return TurnSaveResult.Refused(UNSUPPORTED_FORMAT)
        var result: TurnSaveResult = TurnSaveResult.Saved
        val failure = failureReason {
            result = when {
                isPng(name) -> png.save(entry, quarterTurns)
                isHeic(name) -> heic.save(entry, quarterTurns)
                else -> turn(entry, quarterTurns)
            }
        }
        return failure?.let { TurnSaveResult.Failed(it) } ?: result
    }

    private fun turn(entry: FileHandle, quarterTurns: Int): TurnSaveResult {
        val head = files.readRange(entry, 0L, HEADER_LIMIT)
        val patched = JpegOrientation.rewrite(head) { it.turnedBy(quarterTurns) }
            ?: return TurnSaveResult.Refused(NO_ORIENTATION_TAG)
        val changed = head.indices.filter { head[it] != patched[it] }
        if (changed.isEmpty()) return TurnSaveResult.Saved
        val before = files.readTimestamps(entry)
        val failure = overwriteAndVerify(entry, head, patched, changed.first()..changed.last())
        // Best effort: a modified time that cannot be put back does not undo a good save.
        failureReason { files.writeTimestamps(entry, FileTimestamps(modified = before.modified)) }
        return failure?.let { TurnSaveResult.Failed(it) } ?: TurnSaveResult.Saved
    }

    /** Writes the changed range, reads the header back and compares it; on a mismatch the old bytes go back. */
    private fun overwriteAndVerify(entry: FileHandle, head: ByteArray, patched: ByteArray, range: IntRange): String? {
        val start = range.first.toLong()
        val written = failureReason {
            files.overwriteAt(entry, start, patched.copyOfRange(range.first, range.last + 1))
        }
        val verified = written == null && files.readRange(entry, 0L, head.size).contentEquals(patched)
        if (verified) return null
        files.overwriteAt(entry, start, head.copyOfRange(range.first, range.last + 1))
        return written ?: DID_NOT_TAKE
    }

    companion object {
        fun isJpeg(name: String): Boolean = splitStemAndSuffix(name).second.lowercase() in JPEG_EXTENSIONS

        fun isHeic(name: String): Boolean = splitStemAndSuffix(name).second.lowercase() in heicExtensions

        fun isPng(name: String): Boolean = splitStemAndSuffix(name).second.lowercase() == PNG_EXTENSION

        /** A JPEG, a PNG and a HEIC can be saved turned. */
        fun canSave(name: String): Boolean = isJpeg(name) || isPng(name) || isHeic(name)

        const val HEADER_LIMIT = 256 * KIBIBYTE
        const val UNSUPPORTED_FORMAT = "Only JPEG, PNG and HEIC photos can be saved turned for now"
        const val NO_ORIENTATION_TAG = "This JPEG has no orientation tag to change"
        private const val DID_NOT_TAKE = "The photo did not take the change"
        private val JPEG_EXTENSIONS = setOf(".jpg", ".jpeg")
        private const val PNG_EXTENSION = ".png"
    }
}
