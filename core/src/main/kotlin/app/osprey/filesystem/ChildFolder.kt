package app.osprey.filesystem

/**
 * The folder called [name] directly inside [parent], or null. The exact name wins; otherwise one that differs only by
 * case, since many file systems take both for one folder (#132): a user's `trash` is the `Trash`.
 */
fun FileSystemRepository.childFolder(parent: FileHandle, name: String): FileHandle? {
    val folders = list(parent).filter { it.isDirectory }
    val found = folders.firstOrNull { it.name == name }
        ?: folders.firstOrNull { it.name.equals(name, ignoreCase = true) }
    return found?.handle
}
