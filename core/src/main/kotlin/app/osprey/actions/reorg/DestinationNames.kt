package app.osprey.actions.reorg

import app.osprey.actions.TakenNames
import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/** The names taken in each `<output>/<year>` folder, existing or already planned, compared ignoring case. */
internal class DestinationNames(private val fs: FileSystemRepository, private val output: FileHandle) {

    private val taken = mutableMapOf<Int, TakenNames>()

    /** Reserves and returns [name] in [year]'s folder, or `stem__yearN.ext` if it is taken. */
    fun reserve(year: Int, name: String): String {
        val names = taken.getOrPut(year) { existingNames(year) }
        val (stem, suffix) = splitStemAndSuffix(name)
        var candidate = name
        var n = 0
        while (candidate in names) {
            n++
            candidate = "${stem}__year$n$suffix"
        }
        names.claim(candidate)
        return candidate
    }

    private fun existingNames(year: Int): TakenNames {
        val folder = fs.list(output).firstOrNull { it.isDirectory && it.name == year.toString() }
            ?: return TakenNames()
        return TakenNames(fs.list(folder.handle).map { it.name })
    }
}
