package app.osprey.actions.trash

/**
 * The action that parked a file in the Trash; its [folderName] is the subfolder under `Trash/<year>/`.
 * [REPLACED] holds a library file that a restore swapped for the Trash's copy.
 */
enum class TrashAction(val folderName: String) {
    DEDUPE("Dedupe"),
    VISUAL_DUPES("VisualDupes"),
    REPLACED("Replaced"),
}
