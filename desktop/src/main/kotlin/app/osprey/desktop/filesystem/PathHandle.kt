package app.osprey.desktop.filesystem

import app.osprey.filesystem.FileHandle
import java.nio.file.Path

/** Real [FileHandle]: a plain [Path] on Windows/Unix. */
data class PathHandle(val path: Path) : FileHandle
