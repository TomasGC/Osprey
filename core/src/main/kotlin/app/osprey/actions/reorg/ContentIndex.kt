package app.osprey.actions.reorg

import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Which file contents are already known, found by size first and hashed only when a size matches,
 * so a library of large videos is not read in full on every scan.
 */
internal class ContentIndex(private val fs: FileSystemRepository) {

    private val bySize = mutableMapOf<Long, MutableList<FileHandle>>()
    private val hashes = mutableMapOf<FileHandle, String>()

    fun add(entry: FileEntry) {
        bySize.getOrPut(entry.sizeBytes) { mutableListOf() } += entry.handle
    }

    /** The first known file with the same size and the same content as [entry], or null. */
    fun firstMatch(entry: FileEntry): FileHandle? {
        val candidates = bySize[entry.sizeBytes] ?: return null
        val hash = hashOf(entry.handle)
        return candidates.firstOrNull { hashOf(it) == hash }
    }

    private fun hashOf(handle: FileHandle): String = hashes.getOrPut(handle) { fs.contentHash(handle) }
}
