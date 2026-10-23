package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Deletes files from the Trash: the only irreversible step in Osprey. It acts on the entries it is
 * given, but only after finding each one again under `Trash/<year>/<action>` of the library, so a
 * handle that is not there (moved, already gone, outside the Trash) is never deleted. A folder the
 * clear leaves empty is removed with it, up to but never including `Trash`.
 */
class TrashClearer(private val fs: FileSystemRepository) {

    /**
     * Deletes [entries] one by one. [shouldStop] is polled before each file, [progress] told which
     * one is next. A file that cannot be deleted is listed in the result and the run carries on.
     */
    fun clear(
        libraryRoot: FileHandle,
        entries: Collection<TrashEntry>,
        shouldStop: () -> Boolean = { false },
        progress: (TrashProgress) -> Unit = {},
    ): TrashClearResult {
        val locator = TrashLocator(fs, libraryRoot)
        val failures = mutableListOf<TrashFailure>()
        val touched = linkedSetOf<Pair<String, TrashAction>>()
        var deleted = 0
        var cancelled = false
        for ((index, entry) in entries.withIndex()) {
            cancelled = shouldStop()
            if (cancelled) break
            progress(TrashProgress(index, entries.size, entry.label))
            touched += entry.year to entry.action
            val failure = attempt(entry.label) { delete(locator, entry) }
            if (failure == null) deleted++ else failures += failure
        }
        val pruned = touched.sumOf { (year, action) -> locator.prune(year, action) }
        return TrashClearResult(deleted, failures, pruned, cancelled)
    }

    private fun delete(locator: TrashLocator, entry: TrashEntry) {
        val file = checkNotNull(locator.find(entry)) { "not in the Trash any more" }
        fs.delete(file.handle)
    }
}
