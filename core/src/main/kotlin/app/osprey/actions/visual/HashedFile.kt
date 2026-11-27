package app.osprey.actions.visual

import app.osprey.filesystem.FileEntry

/** A file and the perceptual hash of its frame. */
data class HashedFile(val entry: FileEntry, val hash: Long)
