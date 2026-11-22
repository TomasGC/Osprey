package app.osprey.actions.generated

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileTimestamps
import app.osprey.media.FakeMediaMetadataReader

/** A fake file system and reader holding generated files, content and metadata as their specs say. */
class MixedFolder {
    val fs = FakeFileSystemRepository()
    val reader = FakeMediaMetadataReader()
    val root: FileHandle = fs.root

    fun add(parent: FileHandle, spec: FileSpec): FileHandle {
        val handle = fs.addFile(parent, spec.name, spec.content)
        spec.metadata.asMediaDate()?.let { reader.setCaptureDateTime(handle, it) }
        return handle
    }

    /** Adds every spec of [mix] directly in [parent]; the handle of each by spec id (a rename keeps it). */
    fun addAll(mix: List<FileSpec>, parent: FileHandle = root): Map<Int, FileHandle> =
        mix.associate { it.id to add(parent, it) }

    /** The content of every file under the root, sorted: what must survive any action. */
    fun contents(): List<String> = filesUnder(root).map { String(fs.readBytes(it)) }.sorted()

    /**
     * Gives every file the metadata of the spec whose content it holds. A copy is a new file in the fake, with no
     * metadata of its own; a real reader reads a copy's metadata from the copy itself.
     */
    fun readMetadataFromContent(mix: List<FileSpec>) {
        val byContent = mix.associateBy { String(it.content) }
        filesUnder(root).forEach { file ->
            byContent[String(fs.readBytes(file))]?.metadata?.asMediaDate()?.let { reader.setCaptureDateTime(file, it) }
        }
    }

    fun timestamps(handles: Map<Int, FileHandle>): Map<Int, FileTimestamps> =
        handles.mapValues { fs.readTimestamps(it.value) }

    private fun filesUnder(folder: FileHandle): List<FileHandle> = fs.list(folder).flatMap {
        if (it.isDirectory) filesUnder(it.handle) else listOf(it.handle)
    }
}
