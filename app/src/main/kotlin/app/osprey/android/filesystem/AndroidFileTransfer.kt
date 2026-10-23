package app.osprey.android.filesystem

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileTransfer
import app.osprey.filesystem.sha256Hex

private const val PARTIAL_SUFFIX = ".osprey-part"
private const val PARTIAL_MIME_TYPE = "application/octet-stream"

/**
 * [FileTransfer] over SAF. Providers differ, so a copy is a stream copy (`moveDocument` only works
 * inside one provider); it goes to a temporary name and is renamed at the end, so the final name
 * only ever holds a complete file.
 */
class AndroidFileTransfer(private val context: Context) : FileTransfer {

    override fun copyTo(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        val source = (entry as AndroidFileHandle).documentFile
        val destDoc = (destFolder as AndroidFileHandle).documentFile
        val targetName = newName ?: source.name.orEmpty()
        if (destDoc.findFile(targetName) != null) {
            throw FileAlreadyExistsException("$targetName already exists in ${destDoc.name}")
        }
        val partial = destDoc.createFile(PARTIAL_MIME_TYPE, targetName + PARTIAL_SUFFIX)
            ?: error("copy failed: cannot create $targetName in ${destDoc.name}")
        var finished = false
        try {
            streamCopy(source, partial)
            check(partial.renameTo(targetName)) { "copy failed: cannot rename to $targetName" }
            finished = true
        } finally {
            if (!finished) partial.delete()
        }
        return AndroidFileHandle(destDoc.findFile(targetName) ?: error("copy produced no document"))
    }

    private fun streamCopy(source: DocumentFile, target: DocumentFile) {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(source.uri) ?: error("cannot open ${source.uri}")
        input.use { reader ->
            val output = resolver.openOutputStream(target.uri) ?: error("cannot open ${target.uri}")
            output.use { writer -> reader.copyTo(writer) }
        }
    }

    override fun delete(entry: FileHandle) {
        val doc = (entry as AndroidFileHandle).documentFile
        check(doc.delete()) { "delete failed: ${doc.name}" }
    }

    override fun contentHash(entry: FileHandle): String {
        val uri = (entry as AndroidFileHandle).documentFile.uri
        return context.contentResolver.openInputStream(uri)?.use { sha256Hex(it) } ?: error("cannot open $uri")
    }
}
