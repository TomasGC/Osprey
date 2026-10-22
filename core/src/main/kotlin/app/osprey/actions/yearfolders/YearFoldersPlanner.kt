package app.osprey.actions.yearfolders

import app.osprey.actions.imageExtensions
import app.osprey.actions.resolveMediaDate
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaMetadataReader

private val YEAR_FOLDER_NAME = Regex("""\d{4}""")

/**
 * Groups the images and videos of a library root into `<root>/<year>/` folders by capture date.
 * Every file directly in `root`, and every file directly in a year folder whose capture year
 * differs from that folder's, is moved to the folder of its own year. The year comes from
 * [resolveMediaDate], the same resolution Dates and Standardize use, so the three actions cannot
 * disagree. A file with no resolvable date stays where it is.
 */
class YearFoldersPlanner(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
) {

    /**
     * Scans `root`'s immediate files and the immediate files of its year folders (a folder named
     * with exactly four digits). Every other subfolder — `TODELETE`, `TODELETEDUPS`, anything
     * else — is never entered. A file that is neither image nor video is ignored entirely. Nothing
     * on disk changes: call [apply] to act on the result.
     */
    fun plan(root: FileHandle): YearFoldersPlan {
        val rootEntries = fs.list(root)
        val reserved = mutableMapOf<Int, MutableSet<String>>()
        val moves = mutableListOf<YearFoldersMove>()
        val undated = mutableListOf<FileEntry>()

        for ((entry, folder) in candidates(rootEntries).filter { isMedia(it.first.name) }) {
            val year = resolveMediaDate(entry.handle, entry.name, mediaMetadataReader)?.dateTime?.year
            if (year == null) {
                undated += entry
            } else if (folder?.name != year.toString()) {
                val taken = reserved.getOrPut(year) { existingNames(rootEntries, year) }
                moves += moveTo(year, entry, folder, taken)
            }
        }
        return YearFoldersPlan(moves, undated)
    }

    /** Each file in the root or directly in a year folder, paired with that year folder (null for the root). */
    private fun candidates(rootEntries: List<FileEntry>): List<Pair<FileEntry, FileEntry?>> {
        val rootFiles = rootEntries.filterNot { it.isDirectory }.map { it to null }
        val yearFolderFiles = rootEntries.filter { it.isDirectory && YEAR_FOLDER_NAME.matches(it.name) }
            .flatMap { folder -> fs.list(folder.handle).filterNot { it.isDirectory }.map { it to folder } }
        return rootFiles + yearFolderFiles
    }

    private fun moveTo(year: Int, entry: FileEntry, folder: FileEntry?, taken: MutableSet<String>): YearFoldersMove {
        val (destName, note) = resolveDestination(entry.name, taken)
        taken += destName
        val sourcePath = if (folder == null) entry.name else "${folder.name}/${entry.name}"
        return YearFoldersMove(entry.handle, sourcePath, year, destName, note)
    }

    private fun existingNames(rootEntries: List<FileEntry>, year: Int): MutableSet<String> {
        val folder = rootEntries.firstOrNull { it.isDirectory && it.name == year.toString() } ?: return mutableSetOf()
        return fs.list(folder.handle).map { it.name }.toMutableSet()
    }

    private fun isMedia(name: String): Boolean {
        val suffix = splitStemAndSuffix(name).second.lowercase()
        return suffix in imageExtensions || suffix in videoExtensions
    }

    private fun resolveDestination(name: String, taken: Set<String>): Pair<String, String> {
        if (name !in taken) return name to ""
        val (stem, suffix) = splitStemAndSuffix(name)
        var n = 1
        while ("${stem}__year$n$suffix" in taken) n++
        return "${stem}__year$n$suffix" to "(renamed, name collision)"
    }

    /**
     * Logs one `YEAR` line per move, e.g. `YEAR 2019/a.jpg -> 2016/a.jpg`, then one `UNDATED` line
     * per file left in place. Returns the move count.
     */
    fun describe(plan: YearFoldersPlan, log: (String) -> Unit): Int {
        for (move in plan.moves) {
            val suffix = if (move.note.isNotEmpty()) "  ${move.note}" else ""
            log("  YEAR ${move.sourcePath} -> ${move.year}/${move.destName}$suffix")
        }
        for (entry in plan.undated) {
            log("  UNDATED ${entry.name}")
        }
        return plan.moves.size
    }

    /** Creates each needed year folder under [root] (reusing an existing one), then moves every file in [plan]. */
    fun apply(plan: YearFoldersPlan, root: FileHandle) {
        val folders = plan.moves.map { it.year }.distinct().associateWith { fs.createFolder(root, it.toString()) }
        for (move in plan.moves) {
            fs.move(move.source, folders.getValue(move.year), move.destName)
        }
    }
}
