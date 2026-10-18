package app.osprey.actions.flatten

import app.osprey.filesystem.FileHandle

/**
 * One now-nested subfolder to remove once its files have been moved out, deepest first.
 *
 * @property relativePath The folder's path relative to the folder being flattened (e.g. `06/15`).
 */
data class FlattenFolder(val handle: FileHandle, val relativePath: String)
