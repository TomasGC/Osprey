package app.osprey.actions.trash

/** The action that parked a file in the Trash; its [folderName] is the subfolder under `Trash/<year>/`. */
enum class TrashAction(val folderName: String) {
    DEDUPE("Dedupe"),
    VISUAL_DUPES("VisualDupes"),
}
