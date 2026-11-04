package app.osprey.history

import java.io.File
import java.time.Instant

/** The history kept in [dir], with the runs a previous launch left unfinished closed as interrupted at [at]. */
fun openHistory(dir: File, at: Instant = Instant.now()): JobHistoryStore =
    FileJobHistoryStore(dir).also { it.closeInterrupted(at) }
