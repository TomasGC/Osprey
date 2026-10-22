package app.osprey.actions.yearfolders

import app.osprey.filesystem.FileEntry

/**
 * The result of [YearFoldersPlanner.plan]: nothing has moved yet.
 *
 * @property moves Files to move into a year folder.
 * @property undated Image/video files whose capture date could not be resolved; they stay where
 * they are.
 */
data class YearFoldersPlan(
    val moves: List<YearFoldersMove>,
    val undated: List<FileEntry>,
)
