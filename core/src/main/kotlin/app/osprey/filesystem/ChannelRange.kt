package app.osprey.filesystem

import java.io.IOException
import java.nio.ByteBuffer

/**
 * Reads and overwrites a byte range of a file through its positional `read(buffer, position)` and
 * `write(buffer, position)` (#61), the one place both platforms get the rules from: a read stops at the end of the
 * file, a write never goes past it, and neither loops forever on a channel that makes no progress.
 */
object ChannelRange {

    /** Up to [length] bytes from [offset] of a file [size] bytes long; none past its end. */
    fun read(size: Long, offset: Long, length: Int, readAt: (ByteBuffer, Long) -> Int): ByteArray {
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), (size - offset).coerceAtLeast(0L)).toInt())
        while (buffer.hasRemaining()) {
            if (readAt(buffer, offset + buffer.position()) <= 0) throw IOException("the file ended before its size")
        }
        return buffer.array()
    }

    /** Writes [bytes] at [offset] inside a file [size] bytes long; throws when the range is not inside it. */
    fun write(size: Long, offset: Long, bytes: ByteArray, writeAt: (ByteBuffer, Long) -> Int) {
        if (offset < 0 || offset + bytes.size > size) throw IOException("range is outside the file")
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            if (writeAt(buffer, offset + buffer.position()) <= 0) throw IOException("the file took no bytes")
        }
    }
}
