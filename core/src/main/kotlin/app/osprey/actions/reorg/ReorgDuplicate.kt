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

/** This scanned file as one side of a pair, with the [date] the caller resolved for it. */
internal fun ScannedFile.asSide(label: String, year: String, date: LocalDateTime?) =
    DuplicateSide(entry.handle, label, entry.name, entry.sizeBytes, date, year)

/** What the user decided for one [duplicate]: a ticked side is kept, an unticked side goes to the Trash. */
data class DuplicateChoice(val duplicate: ReorgDuplicate, val keepInput: Boolean, val keepLibrary: Boolean)

/** An input file of a Move input whose content, verified identical, is already in the library. */
data class ReorgDuplicate(val input: DuplicateSide, val library: DuplicateSide)
