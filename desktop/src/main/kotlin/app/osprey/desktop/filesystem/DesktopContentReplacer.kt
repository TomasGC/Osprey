package app.osprey.desktop.filesystem

import app.osprey.filesystem.BackedUpReplace.Companion.DID_NOT_TAKE
import app.osprey.filesystem.ChannelRange
import app.osprey.filesystem.ContentReplacer
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.failureReason
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

private const val PART_MARKER = ".osprey-part-"

/**
 * Replaces a whole file on plain paths (#70). A sibling copy that carries the original's attributes (permissions,
 * read-only flag) is rewritten, read back and moved over the original in one step, so the file holds the old content
 * or the new, never half of each.
 */
class DesktopContentReplacer : ContentReplacer {

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
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
