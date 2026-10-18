package app.osprey.filesystem

/**
 * Platform-agnostic access to one storage tree: plain Windows/Unix paths on desktop, Android's
 * Storage Access Framework on Android. Every method takes and returns a [FileHandle] — never a
 * `java.nio.file.Path` or a SAF `Uri`/`DocumentFile` — so planning logic in `:core` never has to
 * know which platform it is running on.
 */
interface FileSystemRepository {

    /**
     * The immediate children of [folder] — files and subfolders, not recursive.
     * Returns an empty list if [folder] does not exist or is not a directory: a missing or
     * vanished folder is not an error, it simply has nothing in it.
     */
    fun list(folder: FileHandle): List<FileEntry>

    /**
     * Renames [entry] to [newName], within its current parent folder.
     * Never overwrites: throws [FileAlreadyExistsException] if [newName] is already taken there.
     */
    fun rename(entry: FileHandle, newName: String): FileHandle

    /**
     * Moves [entry] into [destFolder], keeping its current name unless [newName] is given.
     * Never overwrites: throws [FileAlreadyExistsException] on a name collision in [destFolder].
     */
    fun move(entry: FileHandle, destFolder: FileHandle, newName: String? = null): FileHandle

    /**
     * Ensures a subfolder named [name] exists directly inside [parent], creating it if needed.
     * Idempotent when [name] already exists there as a folder (matching the POC's
     * `mkdir(exist_ok=True)` for `TODELETE`); throws [FileAlreadyExistsException] if [name]
     * already exists there as a file.
     */
    fun createFolder(parent: FileHandle, name: String): FileHandle

    /** The full contents of [entry], for content hashing. */
    fun readBytes(entry: FileHandle): ByteArray

    /** [entry]'s current timestamps, where the platform exposes them. */
    fun readTimestamps(entry: FileHandle): FileTimestamps

    /**
     * Sets [entry]'s timestamps to [timestamps]'s non-null fields; a null field leaves that
     * timestamp untouched.
     */
    fun writeTimestamps(entry: FileHandle, timestamps: FileTimestamps)
}
