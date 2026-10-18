package app.osprey.actions.rename

import app.osprey.filesystem.FileHandle

/** One file to rename (or, if [RenamePlan.skips] holds it, a collision that was skipped instead). */
data class RenameAction(val source: FileHandle, val sourceName: String, val targetName: String)
