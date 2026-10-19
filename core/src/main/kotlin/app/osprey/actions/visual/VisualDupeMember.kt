package app.osprey.actions.visual

import app.osprey.filesystem.FileHandle

/** One member of a visual-duplicate group, heaviest-first within [VisualDupePlan.groups]. */
data class VisualDupeMember(val handle: FileHandle, val name: String, val sizeBytes: Long)
