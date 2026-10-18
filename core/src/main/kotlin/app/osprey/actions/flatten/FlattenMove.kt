package app.osprey.actions.flatten

import app.osprey.filesystem.FileHandle

/**
 * One file to move up into the folder being flattened.
 *
 * @property source The file's current handle, still inside its nested subfolder.
 * @property sourcePath The file's path relative to the folder being flattened (e.g. `06/a.jpg`),
 * for display only.
 * @property destName The name it will have once moved — usually unchanged, suffixed with
 * `__flatN` only when [note] explains a name collision at the destination.
 * @property note Empty, or a short explanation (e.g. "(renamed, name collision)").
 */
data class FlattenMove(
    val source: FileHandle,
    val sourcePath: String,
    val destName: String,
    val note: String,
)
