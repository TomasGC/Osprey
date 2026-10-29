package app.osprey.android.filesystem

import android.content.Context
import app.osprey.filesystem.BackedUpReplace
import app.osprey.filesystem.ChannelRange
import app.osprey.filesystem.ContentReplacer
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileTransfer
import java.io.FileOutputStream

/**
 * Replaces a whole document over SAF (#70). SAF cannot swap a file in, so the document is rewritten in place after a
 * verified copy of it has been made beside it; [BackedUpReplace] keeps the copy until the new content is read back and
 * puts the original back if it is not, so the handle stays valid and a failed write never leaves a truncated photo.
 */
class AndroidContentReplacer(
    private val context: Context,
    private val transfer: FileTransfer = AndroidFileTransfer(context),
) : ContentReplacer {

    override fun replaceContent(entry: FileHandle, bytes: ByteArray) {
        val doc = entry.documentFile()
        val folder = doc.parentFile ?: error("cannot replace ${doc.name}: no parent")
        BackedUpReplace(transfer, context::readAllBytes)
            .replace(entry, AndroidFileHandle(folder), doc.name.orEmpty(), bytes, ::truncateAndWrite)
    }

    // "rwt" truncates before it writes: the one unsafe write here, which BackedUpReplace is what makes safe.
    private fun truncateAndWrite(entry: FileHandle, bytes: ByteArray) {
        context.contentResolver.openDescriptor(entry, "rwt").use { pfd ->
            FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                ChannelRange.writeAll(bytes) { buffer, at -> channel.write(buffer, at) }
                channel.force(true)
            }
        }
    }
}
