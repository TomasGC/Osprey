package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle

/** One file waiting in `Trash/[year]/[action]`. */
data class TrashEntry(
    val year: String,
    val action: TrashAction,
    val handle: FileHandle,
    val name: String,
    val sizeBytes: Long,
) {
    /** Where it sits, for messages: `Trash/2019/Dedupe/a.jpg`. */
    val label: String get() = "Trash/$year/${action.folderName}/$name"
}
