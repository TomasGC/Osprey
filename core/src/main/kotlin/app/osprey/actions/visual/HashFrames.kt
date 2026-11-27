package app.osprey.actions.visual

import app.osprey.filesystem.FileEntry
import app.osprey.jobs.JobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.media.MediaMetadataReader
import java.util.concurrent.Executors

/** The perceptual hash of [entry]'s frame at the hash size, or null when it cannot be decoded. */
fun MediaMetadataReader.frameHash(entry: FileEntry): Long? =
    decodeFrame(entry.handle, HASH_DECODE_MAX_SIDE)?.let(::phash)

/**
 * [hash] of each of [files], at most [workers] at once, in the order of [files] whatever finishes first; a file with no
 * hash is left out. [control] is only used from the calling thread: it is asked before each file starts, and told then
 * how many are finished. After a stop nothing starts, the files being hashed are finished, and the result is null.
 * A failure is thrown once what runs has ended, the first in file order, as hashing them one by one would.
 * One worker hashes on the calling thread. With more, [hash] must be safe to call from several threads.
 */
fun hashFrames(
    files: List<FileEntry>,
    workers: Int,
    control: JobControl,
    hash: (FileEntry) -> Long?,
): List<HashedFile>? = if (workers <= 1) {
    hashOneByOne(files, control, hash)
} else {
    hashTogether(files, workers, control, hash)
}

private fun hashOneByOne(files: List<FileEntry>, control: JobControl, hash: (FileEntry) -> Long?): List<HashedFile>? {
    val hashed = mutableListOf<HashedFile>()
    val all = control.forEachUntilStopped(files, FileEntry::name) { entry ->
        hash(entry)?.let { hashed += HashedFile(entry, it) }
    }
    return hashed.takeIf { all }
}

private fun hashTogether(
    files: List<FileEntry>,
    workers: Int,
    control: JobControl,
    hash: (FileEntry) -> Long?,
): List<HashedFile>? {
    val pool = Executors.newFixedThreadPool(workers)
    try {
        return ParallelHashing(files, workers, control, hash, pool).run()
    } finally {
        pool.shutdown()
    }
}
