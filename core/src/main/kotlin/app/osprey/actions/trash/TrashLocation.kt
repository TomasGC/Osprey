package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle

/** Where a year's trash lives: under [libraryRoot], in `Trash/[year]/<action>`. */
data class TrashLocation(val libraryRoot: FileHandle, val year: String)
