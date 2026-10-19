package app.osprey.filesystem

/**
 * Thrown by [FileSystemRepository.rename], [FileSystemRepository.move] and
 * [FileSystemRepository.createFolder] when the destination name is already taken.
 * None of these three operations ever silently overwrites — callers are expected to resolve
 * collisions themselves first via [FileSystemRepository.list], exactly as the Python POC's
 * `plan_*` functions do before calling `rename`/`mkdir`.
 */
class FileAlreadyExistsException(message: String, cause: Throwable? = null) : Exception(message, cause)
