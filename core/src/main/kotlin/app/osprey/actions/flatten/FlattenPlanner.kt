package app.osprey.actions.flatten

import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.forEachUntilStopped

private const val TODELETE = "TODELETE"

/**
 * Collapses nested `<year>/<month>/[<day>]/files` folders into [FlattenPlanner.plan]'s `root`:
 * every file in every subfolder moves up to `root` directly, then the now-empty subfolders are
 * removed. A straight re-platform of the POC's `plan_flatten`/`apply_flatten` against
 * [FileSystemRepository] instead of real paths — no behavior change.
 */
class FlattenPlanner(private val fs: FileSystemRepository) {

    /**
     * Scans `root`: every file nested one or more levels deep is queued to move directly into
     * `root`, deepest subfolders listed first. Nothing on disk changes — call [apply] to act on
     * the result. Any subfolder named [TODELETE], at any depth, is skipped entirely (neither its
     * files nor its own nested subfolders are touched), matching every other action's rule of
     * never touching anything already parked there.
     */
    fun plan(root: FileHandle, control: JobControl = NoJobControl): FlattenPlan {
        val folders = collectNestedFolders(root)
        val existingNames = fs.list(root).map { it.name }.toMutableSet()
        val reserved = mutableSetOf<String>()
        val moves = mutableListOf<FlattenMove>()

        control.forEachUntilStopped(folders, FlattenFolder::relativePath) { folder ->
            for (entry in fs.list(folder.handle)) {
                if (entry.isDirectory) continue
                val (destName, note) = resolveDestination(entry.name, existingNames, reserved)
                reserved += destName
                moves += FlattenMove(entry.handle, "${folder.relativePath}/${entry.name}", destName, note)
            }
        }
        return FlattenPlan(moves, folders)
    }

    private fun resolveDestination(
        name: String,
        existingNames: Set<String>,
        reserved: Set<String>,
    ): Pair<String, String> {
        if (name !in existingNames && name !in reserved) return name to ""
        var n = 1
        var candidate = flatCandidateName(name, n)
        while (candidate in existingNames || candidate in reserved) {
            n++
            candidate = flatCandidateName(name, n)
        }
        return candidate to "(renamed, name collision)"
    }

    /** Logs one line per move in [plan], e.g. `FLATTEN 06/a.jpg -> a.jpg`. Returns the move count. */
    fun describe(plan: FlattenPlan, log: (String) -> Unit): Int {
        for (move in plan.moves) {
            val suffix = if (move.note.isNotEmpty()) "  ${move.note}" else ""
            log("  FLATTEN ${move.sourcePath} -> ${move.destName}$suffix")
        }
        return plan.moves.size
    }

    /**
     * Moves every file in [plan] into `root`, then removes [plan]'s subfolders deepest first, and returns
     * how many files it moved. A subfolder that gained an untracked file after [plan] was computed is left
     * in place and logged, exactly like the POC's `rmdir()`-then-log-and-continue. A run [control] stopped
     * removes no folder: the files it left would only make them fail.
     */
    fun apply(plan: FlattenPlan, root: FileHandle, control: JobControl = NoJobControl, log: (String) -> Unit): Int {
        var moved = 0
        val finished = control.forEachUntilStopped(plan.moves, FlattenMove::sourcePath) { move ->
            fs.move(move.source, root, move.destName)
            moved++
        }
        if (!finished) return moved
        for (folder in plan.folders) {
            try {
                fs.deleteEmptyFolder(folder.handle)
            } catch (_: DirectoryNotEmptyException) {
                log("  could not remove ${folder.relativePath} (not empty)")
            }
        }
        return moved
    }

    private fun collectNestedFolders(root: FileHandle): List<FlattenFolder> {
        data class Queued(val folder: FlattenFolder, val depth: Int)

        val found = mutableListOf<Queued>()
        val queue = ArrayDeque<Queued>()
        for (entry in fs.list(root)) {
            if (entry.isDirectory && entry.name != TODELETE) {
                queue += Queued(FlattenFolder(entry.handle, entry.name), 1)
            }
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            found += current
            for (child in fs.list(current.folder.handle)) {
                if (child.isDirectory && child.name != TODELETE) {
                    val childFolder = FlattenFolder(child.handle, "${current.folder.relativePath}/${child.name}")
                    queue += Queued(childFolder, current.depth + 1)
                }
            }
        }
        return found.sortedByDescending { it.depth }.map { it.folder }
    }

    private fun flatCandidateName(originalName: String, n: Int): String {
        val (stem, suffix) = splitStemAndSuffix(originalName)
        return "${stem}__flat$n$suffix"
    }
}
