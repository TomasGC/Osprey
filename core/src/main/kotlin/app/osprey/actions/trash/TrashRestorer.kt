package app.osprey.actions.trash

import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val NOT_IN_TRASH = "not in the Trash any more"

/**
 * Puts Trash files back into `<library>/<year>/`. A file is moved, never copied, and a name that is
 * already taken is never overwritten: it becomes a [RestoreConflict] for the user to settle.
 */
class TrashRestorer(private val fs: FileSystemRepository) {

    /** Splits [entries] into those whose name is free in the library and those that collide; changes nothing. */
    fun plan(libraryRoot: FileHandle, entries: Collection<TrashEntry>): RestorePlan {
        val libraryNames = mutableMapOf<String, Map<String, FileEntry>>()
        val direct = mutableListOf<TrashEntry>()
        val conflicts = mutableListOf<RestoreConflict>()
        for (entry in entries) {
            val names = libraryNames.getOrPut(entry.year) { namesInYear(libraryRoot, entry.year) }
            val existing = names[entry.name]
            if (existing == null) direct += entry else conflicts += RestoreConflict(entry, existing)
        }
        return RestorePlan(direct, conflicts)
    }

    /**
     * Restores the [RestorePlan.direct] files; the conflicts are left for [resolve]. [shouldStop] is polled
     * before each file. A file that cannot be moved stays in the Trash and is listed in the result.
     */
    fun restore(
        libraryRoot: FileHandle,
        plan: RestorePlan,
        shouldStop: () -> Boolean = { false },
        progress: (TrashProgress) -> Unit = {},
    ): TrashRestoreResult {
        val locator = TrashLocator(fs, libraryRoot)
        val failures = mutableListOf<TrashFailure>()
        val touched = linkedSetOf<Pair<String, TrashAction>>()
        var restored = 0
        var cancelled = false
        for ((index, entry) in plan.direct.withIndex()) {
            cancelled = shouldStop()
            if (cancelled) break
            progress(TrashProgress(index, plan.direct.size, entry.label))
            touched += entry.year to entry.action
            val failure = attempt(entry.label) { moveBack(locator, libraryRoot, entry, entry.name) }
            if (failure == null) restored++ else failures += failure
        }
        val pruned = touched.sumOf { (year, action) -> locator.prune(year, action) }
        return TrashRestoreResult(restored, failures, pruned, cancelled)
    }

    /** Settles one conflict as [choice]; returns the failure, or null when it worked. */
    fun resolve(libraryRoot: FileHandle, conflict: RestoreConflict, choice: ConflictChoice): TrashFailure? {
        val entry = conflict.entry
        val locator = TrashLocator(fs, libraryRoot)
        val failure = attempt(entry.label) {
            when (choice) {
                ConflictChoice.LIBRARY_COPY -> Unit
                ConflictChoice.BOTH -> moveBack(locator, libraryRoot, entry, freeNameInYear(libraryRoot, entry))
                ConflictChoice.TRASH_COPY -> replaceLibraryFile(locator, libraryRoot, conflict)
            }
        }
        if (failure == null && choice != ConflictChoice.LIBRARY_COPY) locator.prune(entry.year, entry.action)
        return failure
    }

    /** Moves the library's file into `Replaced`, then the Trash's file into its place; undoes the first on failure. */
    private fun replaceLibraryFile(locator: TrashLocator, libraryRoot: FileHandle, conflict: RestoreConflict) {
        val entry = conflict.entry
        val file = checkNotNull(locator.find(entry)) { NOT_IN_TRASH }
        val location = TrashLocation(libraryRoot, entry.year)
        val folders = TrashFolders(fs)
        val replacedFolder = folders.ensure(location, TrashAction.REPLACED)
        val replacedName = freeTrashName(entry.name, folders.existingNames(location, TrashAction.REPLACED))
        val yearFolder = fs.createFolder(libraryRoot, entry.year)
        val parked = fs.move(conflict.existing.handle, replacedFolder, replacedName)
        try {
            fs.move(file.handle, yearFolder)
        } catch (e: FileAlreadyExistsException) {
            fs.move(parked, yearFolder, entry.name)
            throw e
        }
    }

    private fun moveBack(locator: TrashLocator, libraryRoot: FileHandle, entry: TrashEntry, newName: String) {
        val file = checkNotNull(locator.find(entry)) { NOT_IN_TRASH }
        fs.move(file.handle, fs.createFolder(libraryRoot, entry.year), newName)
    }

    private fun freeNameInYear(libraryRoot: FileHandle, entry: TrashEntry): String =
        freeTrashName(entry.name, namesInYear(libraryRoot, entry.year).keys)

    private fun namesInYear(libraryRoot: FileHandle, year: String): Map<String, FileEntry> {
        val folder = fs.list(libraryRoot).firstOrNull { it.isDirectory && it.name == year } ?: return emptyMap()
        return fs.list(folder.handle).associateBy { it.name }
    }
}
