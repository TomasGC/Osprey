package app.osprey.actions.reorg

import app.osprey.filesystem.FileHandle
import app.osprey.settings.InputMode

/**
 * One input folder to gather from.
 *
 * @property folder The folder, or null when it could not be reached (grant revoked, drive gone).
 * @property label A short name for the log.
 * @property mode Whether its files are copied or moved.
 */
data class ReorgSource(val folder: FileHandle?, val label: String, val mode: InputMode)
