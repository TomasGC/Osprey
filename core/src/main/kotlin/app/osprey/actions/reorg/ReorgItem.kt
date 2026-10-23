package app.osprey.actions.reorg

import app.osprey.filesystem.FileHandle

/**
 * One file to put into `<output>/<year>/`.
 *
 * @property source The file's current handle.
 * @property label Where it comes from, for display: the input's label plus its relative path.
 * @property sizeBytes Its size when scanned.
 * @property year The capture year, and the name of the destination folder.
 * @property destName Its name in the destination; suffixed `__yearN` only when [note] says why.
 */
data class ReorgItem(
    val source: FileHandle,
    val label: String,
    val sizeBytes: Long,
    val operation: ReorgOperation,
    val year: Int,
    val destName: String,
    val note: String,
)
