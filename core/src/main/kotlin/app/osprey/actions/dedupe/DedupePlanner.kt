package app.osprey.actions.dedupe

import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.freeTrashName
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Finds byte-identical duplicates among the files directly inside `root` and plans moving all
 * but one of each group into the year's Dedupe trash (`Trash/<year>/Dedupe`). A re-platform of the
 * POC's `plan_dedupe`/`apply_dedupe`/`describe_dedupe`: group by size first (cheap), then by content
 * hash only within same-size groups (expensive) — a real performance decision worth preserving,
 * not an accident of the Python implementation.
 */
class DedupePlanner(private val fs: FileSystemRepository) {

    private val trashFolders = TrashFolders(fs)

    /**
     * Scans `root`'s immediate files — subfolders are never touched, since they simply aren't
     * files. Within each duplicate group, the file with the shortest name wins (ties broken
     * alphabetically, case-insensitive); every other file in the group is queued to move into
     * [trash]'s Dedupe folder, suffixed with `__dupN` if its name is already taken there. Nothing
     * on disk changes — call [apply] to act on the result.
     */
    fun plan(root: FileHandle, trash: TrashLocation): DedupePlan {
        val entries = fs.list(root).filterNot { it.isDirectory }
        val taken = trashFolders.existingNames(trash, TrashAction.DEDUPE).toMutableSet()
        val moves = mutableListOf<DedupeMove>()

        for (sizeGroup in entries.groupBy { it.sizeBytes }.values) {
            if (sizeGroup.size < 2) continue
            for (hashGroup in sizeGroup.groupBy { fileHash(fs.readBytes(it.handle)) }.values) {
                if (hashGroup.size < 2) continue
                val sorted = hashGroup.sortedWith(compareBy({ it.name.length }, { it.name.lowercase() }))
                val keep = sorted.first()
                for (extra in sorted.drop(1)) {
                    val destName = freeTrashName(extra.name, taken)
                    taken += destName
                    moves += DedupeMove(extra.handle, extra.name, destName, keep.name)
                }
            }
        }
        return DedupePlan(moves)
    }

    /** Logs one `TRASH <name>  (dup of <keep>)` line per move. Returns the move count. */
    fun describe(plan: DedupePlan, log: (String) -> Unit): Int {
        for (move in plan.moves) {
            log("  TRASH ${move.sourceName}  (dup of ${move.keepName})")
        }
        return plan.moves.size
    }

    /**
     * Moves every extra file in [plan] into [trash]'s Dedupe folder, creating it only if [plan]
     * actually has moves — a no-op run never creates clutter.
     */
    fun apply(plan: DedupePlan, trash: TrashLocation) {
        if (plan.moves.isEmpty()) return
        val destination = trashFolders.ensure(trash, TrashAction.DEDUPE)
        for (move in plan.moves) {
            fs.move(move.source, destination, move.destName)
        }
    }
}
