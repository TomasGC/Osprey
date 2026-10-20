package app.osprey.desktop

import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

private const val TODELETEDUPS = "TODELETEDUPS"

/** The outcome of [moveUncheckedToTodeleteDups]: matches the POC's `move_dups` return shape. */
data class MoveDupsResult(val moved: List<String>, val skipped: List<String>)

/**
 * Moves every member of [group] not in [keepNames] into `root`'s `TODELETEDUPS` subfolder --
 * #8's `VisualDupePlanner` has no `apply()` on purpose (review-before-apply lives entirely in
 * the UI), so this is where "move unchecked" actually happens, matching the POC's `move_dups`.
 * A member whose file no longer exists is skipped rather than failing the whole group; a name
 * collision in `TODELETEDUPS` gets a `__dupN` suffix, same as #5's Dedupe.
 */
fun moveUncheckedToTodeleteDups(
    fs: FileSystemRepository,
    root: FileHandle,
    group: List<VisualDupeMember>,
    keepNames: Set<String>,
): MoveDupsResult {
    val extras = group.filterNot { it.name in keepNames }
    if (extras.isEmpty()) return MoveDupsResult(emptyList(), emptyList())

    val currentNames = fs.list(root).map { it.name }.toSet()
    val todelete = fs.createFolder(root, TODELETEDUPS)
    val reserved = fs.list(todelete).map { it.name }.toMutableSet()

    val moved = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    for (extra in extras) {
        if (extra.name !in currentNames) {
            skipped += extra.name
            continue
        }
        val targetName = firstFreeName(extra.name, reserved)
        fs.move(extra.handle, todelete, targetName)
        reserved += targetName
        moved += extra.name
    }
    return MoveDupsResult(moved, skipped)
}

private fun firstFreeName(name: String, reserved: Set<String>): String {
    if (name !in reserved) return name
    val (stem, suffix) = splitStemAndSuffix(name)
    var n = 1
    var candidate = "${stem}__dup$n$suffix"
    while (candidate in reserved) {
        n++
        candidate = "${stem}__dup$n$suffix"
    }
    return candidate
}
