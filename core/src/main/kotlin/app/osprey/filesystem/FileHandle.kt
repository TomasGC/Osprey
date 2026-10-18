package app.osprey.filesystem

/**
 * Opaque reference to one entry (file or folder) in a [FileSystemRepository].
 *
 * [FileSystemRepository] never exposes a `java.nio.file.Path` or a SAF `Uri`/`DocumentFile`:
 * desktop's real implementation wraps a path, Android's wraps a SAF handle, and nothing in
 * `:core` can tell the difference or assume either shape.
 */
interface FileHandle
