package app.osprey.actions.dedupe

import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val TODELETE = "TODELETE"

/**
 * Finds byte-identical duplicates among the files directly inside `root` and plans moving all
 * but one of each group into `TODELETE`. A straight re-platform of the POC's
 * `plan_dedupe`/`apply_dedupe`/`describe_dedupe`: group by size first (cheap), then by content
 * hash only within same-size groups (expensive) — a real performance decision worth preserving,
 * not an accident of the Python implementation.
 */
class DedupePlanner(private val fs: FileSystemRepository) {

    /**
     * Scans `root`'s immediate files — subfolders, including `TODELETE` itself, are never
     * touched, since they simply aren't files. Within each duplicate group, the file with the
     * shortest name wins (ties broken alphabetically, case-insensitive); every other file in the
     * group is queued to move into `TODELETE`, suffixed with `__dupN` if its name is already
     * taken there. Nothing on disk changes — call [apply] to act on the result.
     */
    fun plan(root: FileHandle): DedupePlan {
        val entries = fs.list(root).filterNot { it.isDirectory }
        val existingTodeleteNames = existingTodeleteNames(root)
        val reservedNames = mutableSetOf<String>()
        val moves = mutableListOf<DedupeMove>()

        for (sizeGroup in entries.groupBy { it.sizeBytes }.values) {
            if (sizeGroup.size < 2) continue
            for (hashGroup in sizeGroup.groupBy { fileHash(fs.readBytes(it.handle)) }.values) {
                if (hashGroup.size < 2) continue
                val sorted = hashGroup.sortedWith(compareBy({ it.name.length }, { it.name.lowercase() }))
                val keep = sorted.first()
                for (extra in sorted.drop(1)) {
                    val destName = resolveTodeleteName(extra.name, existingTodeleteNames, reservedNames)
                    reservedNames += destName
                    moves += DedupeMove(extra.handle, extra.name, destName, keep.name)
                }
            }
        }
        return DedupePlan(moves)
    }

    /** Logs one `TODELETE <name>  (dup of <keep>)` line per move. Returns the move count. */
    fun describe(plan: DedupePlan, log: (String) -> Unit): Int {
        for (move in plan.moves) {
            log("  TODELETE ${move.sourceName}  (dup of ${move.keepName})")
        }
        return plan.moves.size
    }

    /**
     * Moves every extra file in [plan] into `root`'s `TODELETE` subfolder, creating it only if
     * [plan] actually has moves — a no-op run never creates clutter.
     */
    fun apply(plan: DedupePlan, root: FileHandle) {
        if (plan.moves.isEmpty()) return
        val todelete = fs.createFolder(root, TODELETE)
        for (move in plan.moves) {
            fs.move(move.source, todelete, move.destName)
        }
    }

    private fun existingTodeleteNames(root: FileHandle): Set<String> {
        val todelete = fs.list(root).firstOrNull { it.isDirectory && it.name == TODELETE } ?: return emptySet()
        return fs.list(todelete.handle).map { it.name }.toSet()
    }

    private fun resolveTodeleteName(name: String, existing: Set<String>, reserved: Set<String>): String {
        if (name !in existing && name !in reserved) return name
        val (stem, suffix) = splitStemAndSuffix(name)
        var n = 1
        var candidate = "${stem}__dup$n$suffix"
        while (candidate in existing || candidate in reserved) {
            n++
            candidate = "${stem}__dup$n$suffix"
        }
        return candidate
    }
}
