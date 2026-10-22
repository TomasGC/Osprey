package app.osprey.actions.yearfolders

import app.osprey.filesystem.FileHandle

/**
 * One file to move into its capture-year folder.
 *
 * @property source The file's current handle.
 * @property sourcePath The file's path relative to the library root (e.g. `a.jpg` or `2019/a.jpg`),
 * for display only.
 * @property year The capture year, and the name of the destination folder under the root.
 * @property destName The name it will have once moved — usually unchanged, suffixed with
 * `__yearN` only when [note] explains a name collision in the destination folder.
 * @property note Empty, or a short explanation (e.g. "(renamed, name collision)").
 */
data class YearFoldersMove(
    val source: FileHandle,
    val sourcePath: String,
    val year: Int,
    val destName: String,
    val note: String,
)
