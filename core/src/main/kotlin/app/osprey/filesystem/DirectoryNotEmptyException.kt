package app.osprey.filesystem

/** Thrown by [FileSystemRepository.deleteEmptyFolder] when the folder still has children. */
class DirectoryNotEmptyException(message: String) : Exception(message)
