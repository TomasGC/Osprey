package app.osprey.actions.reorg

import app.osprey.filesystem.FileHandle
import java.time.LocalDateTime

/**
 * One side of a [ReorgDuplicate]: a file as the dialog shows it.
 *
 * @property label Where it is, for display and the log: the input's label or the library year folder, then the path.
 * @property date Its resolved capture date, or null when it has none.
 * @property year The folder it sits in, as named in the library; for an input file, the year it would go to.
 */
data class DuplicateSide(
    val handle: FileHandle,
    val label: String,
    val name: String,
    val sizeBytes: Long,
    val date: LocalDateTime?,
    val year: String,
)

/** An input file of a Move input whose content, verified identical, is already in the library. */
data class ReorgDuplicate(val input: DuplicateSide, val library: DuplicateSide)
