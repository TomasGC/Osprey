package app.osprey.desktop.filesystem

import app.osprey.filesystem.BackedUpReplace.Companion.DID_NOT_TAKE
import app.osprey.filesystem.ChannelRange
import app.osprey.filesystem.ContentReplacer
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.failureReason
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

private const val PART_MARKER = ".osprey-part-"
private const val MOVE_RETRY_STEP_MS = 100L
private const val MOVE_RETRY_LIMIT_MS = 5_000L

private fun atomicMove(from: Path, to: Path) {
    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

/**
 * Replaces a whole file on plain paths (#70). A sibling copy that carries the original's attributes (permissions,
 * read-only flag) is rewritten, read back and moved over the original in one step, so the file holds the old content
 * or the new, never half of each.
 *
 * On Windows the final move is refused while another reader (ffmpeg decoding the photo for the viewer) has the
 * original open without sharing delete, for a fraction of a second. A refused move is retried for up to
 * [MOVE_RETRY_LIMIT_MS] before the failure is reported (#93); any other failure is reported at once.
 */
class DesktopContentReplacer(
    private val move: (Path, Path) -> Unit = ::atomicMove,
    private val pause: (Long) -> Unit = Thread::sleep,
) : ContentReplacer {

    private fun moveWhenReleased(temp: Path, target: Path) {
        var waited = 0L
        while (true) {
            try {
                move(temp, target)
                return
            } catch (denied: AccessDeniedException) {
                if (waited >= MOVE_RETRY_LIMIT_MS) throw denied
                pause(MOVE_RETRY_STEP_MS)
                waited += MOVE_RETRY_STEP_MS
            }
        }
    }

    override fun replaceContent(entry: FileHandle, bytes: ByteArray) {
        val target = (entry as PathHandle).path
        val created = Files.readAttributes(target, BasicFileAttributes::class.java).creationTime()
        val temp = target.resolveSibling("${target.fileName}$PART_MARKER${UUID.randomUUID()}")
        try {
            Files.copy(target, temp, StandardCopyOption.COPY_ATTRIBUTES)
            FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                ChannelRange.writeAll(bytes) { buffer, at -> channel.write(buffer, at) }
                channel.force(true)
            }
            if (!Files.readAllBytes(temp).contentEquals(bytes)) throw IOException(DID_NOT_TAKE)
            moveWhenReleased(temp, target)
        } finally {
            // A read-only copy cannot be deleted on Windows; whatever fails here must not hide the real error.
            temp.toFile().setWritable(true)
            failureReason { Files.deleteIfExists(temp) }
        }
        // Best effort: copying the attributes does not carry the creation time on every file system.
        failureReason {
            Files.getFileAttributeView(target, BasicFileAttributeView::class.java).setTimes(null, null, created)
        }
    }
}
