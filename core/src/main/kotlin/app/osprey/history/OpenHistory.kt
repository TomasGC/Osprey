package app.osprey.history

import java.io.File
import java.time.Instant

/**
 * The history kept in [dir]: the runs that ended a day or more before [at] are removed (#109), and the ones a previous
 * launch left unfinished are closed as interrupted at [at].
 */
fun openHistory(dir: File, at: Instant = Instant.now()): JobHistoryStore =
    FileJobHistoryStore(dir, at).also { it.closeInterrupted(at) }
