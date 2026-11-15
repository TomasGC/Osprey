package app.osprey.actions.trash

/**
 * The action that parked a file in the Trash; its [folderName] is the subfolder under `Trash/<year>/`.
 * [REPLACED] holds a library file that a restore swapped for the Trash's copy. [REORG] holds the files a Reorg Move
 * input and the library both had, which the user chose not to keep (#91).
 */
enum class TrashAction(val folderName: String) {
    DEDUPE("Dedupe"),
    VISUAL_DUPES("VisualDupes"),
    REPLACED("Replaced"),
    REORG("Reorg"),
}
