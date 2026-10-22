package app.osprey.actions.trash

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private val YEAR_FOLDER_NAME = Regex("""\d{4}""")

// The pre-Trash layout: each year folder held its own parking folders, one per action.
private val LEGACY_FOLDERS = mapOf(
    "TODELETE" to TrashAction.DEDUPE,
    "TODELETEDUPS" to TrashAction.VISUAL_DUPES,
)

/**
 * One file to move from an old `<year>/TODELETE*` folder into `Trash/<year>/<action>/`.
 *
 * @property sourcePath Path relative to the library root (e.g. `2019/TODELETE/a.jpg`), for display only.
 * @property destName The name in the Trash — unchanged unless [note] explains a collision.
 */
data class TrashMigrationMove(
    val source: FileHandle,
    val sourcePath: String,
    val year: String,
    val action: TrashAction,
    val destName: String,
    val note: String,
)

/** An old parking folder to remove once its files have been moved out. */
data class TrashMigrationFolder(val handle: FileHandle, val relativePath: String)

data class TrashMigrationPlan(val moves: List<TrashMigrationMove>, val folders: List<TrashMigrationFolder>)

/**
 * Moves the files of every old `<year>/TODELETE` and `<year>/TODELETEDUPS` folder of a library into
 * `Trash/<year>/Dedupe` and `Trash/<year>/VisualDupes`, then removes the emptied old folders.
 */
class TrashMigrationPlanner(private val fs: FileSystemRepository) {

    private val trashFolders = TrashFolders(fs)

    /** True while at least one old parking folder, even an empty one, is left under a year folder. */
    fun hasLegacy(libraryRoot: FileHandle): Boolean = legacyFolders(libraryRoot).isNotEmpty()

    /**
     * Scans every four-digit year folder of [libraryRoot]. Only files directly inside an old folder
     * are queued; a subfolder in one is left alone. Nothing on disk changes: call [apply] to act.
     */
    fun plan(libraryRoot: FileHandle): TrashMigrationPlan {
        val reserved = mutableMapOf<Pair<String, TrashAction>, MutableSet<String>>()
        val moves = mutableListOf<TrashMigrationMove>()
        val folders = mutableListOf<TrashMigrationFolder>()

        for (legacy in legacyFolders(libraryRoot)) {
            folders += TrashMigrationFolder(legacy.folder.handle, "${legacy.year}/${legacy.folder.name}")
            val taken = reserved.getOrPut(legacy.year to legacy.action) {
                trashFolders.existingNames(TrashLocation(libraryRoot, legacy.year), legacy.action).toMutableSet()
            }
            for (file in fs.list(legacy.folder.handle).filterNot { it.isDirectory }) {
                val destName = freeTrashName(file.name, taken)
                taken += destName
                val note = if (destName == file.name) "" else "(renamed, name collision)"
                val sourcePath = "${legacy.year}/${legacy.folder.name}/${file.name}"
                moves += TrashMigrationMove(file.handle, sourcePath, legacy.year, legacy.action, destName, note)
            }
        }
        return TrashMigrationPlan(moves, folders)
    }

    /** Logs one line per move, e.g. `TRASH 2019/TODELETE/a.jpg -> Trash/2019/Dedupe/a.jpg`. Returns the move count. */
    fun describe(plan: TrashMigrationPlan, log: (String) -> Unit): Int {
        for (move in plan.moves) {
            val suffix = if (move.note.isNotEmpty()) "  ${move.note}" else ""
            log("  TRASH ${move.sourcePath} -> Trash/${move.year}/${move.action.folderName}/${move.destName}$suffix")
        }
        return plan.moves.size
    }

    /**
     * Moves every file in [plan] into the Trash, then removes the old folders. An old folder that
     * still holds something (a subfolder, or a file that appeared after planning) is left and logged.
     */
    fun apply(plan: TrashMigrationPlan, libraryRoot: FileHandle, log: (String) -> Unit) {
        val destinations = mutableMapOf<Pair<String, TrashAction>, FileHandle>()
        for (move in plan.moves) {
            val destination = destinations.getOrPut(move.year to move.action) {
                trashFolders.ensure(TrashLocation(libraryRoot, move.year), move.action)
            }
            fs.move(move.source, destination, move.destName)
        }
        for (folder in plan.folders) {
            try {
                fs.deleteEmptyFolder(folder.handle)
            } catch (_: DirectoryNotEmptyException) {
                log("  could not remove ${folder.relativePath} (not empty)")
            }
        }
    }

    private data class Legacy(val year: String, val folder: FileEntry, val action: TrashAction)

    private fun legacyFolders(libraryRoot: FileHandle): List<Legacy> =
        fs.list(libraryRoot)
            .filter { it.isDirectory && YEAR_FOLDER_NAME.matches(it.name) }
            .flatMap { year ->
                fs.list(year.handle).mapNotNull { child ->
                    val action = LEGACY_FOLDERS[child.name]
                    if (child.isDirectory && action != null) Legacy(year.name, child, action) else null
                }
            }
}
