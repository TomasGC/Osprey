package app.osprey.settings

/** What a saved input folder does when Reorg runs. */
enum class InputMode { COPY, MOVE }

/**
 * One folder chosen in Settings.
 *
 * @property reference The platform's own way to find it again: a path on desktop, a tree Uri on Android.
 * @property location Where it sits, comparable across folders to catch overlaps.
 * @property displayName A short name for the UI.
 */
data class FolderSetting(val reference: String, val location: FolderLocation, val displayName: String)

data class InputFolderSetting(val folder: FolderSetting, val mode: InputMode = InputMode.COPY)
