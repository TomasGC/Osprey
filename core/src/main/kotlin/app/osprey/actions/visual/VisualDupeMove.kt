package app.osprey.actions.visual

import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.freeTrashName
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Moves every member of [group] not in [keepNames] into [trash]'s VisualDupes folder --
 * [VisualDupePlanner] has no `apply()` on purpose (review-before-apply lives entirely in the
 * UI), so this is where "move unchecked" actually happens, matching the POC's `move_dups`.
 * A member whose file no longer exists in [yearDir] is skipped rather than failing the whole
 * group; a name collision in the trash gets a `__dupN` suffix, same as Dedupe.
 */
fun moveUncheckedToTrash(
    fs: FileSystemRepository,
    trash: TrashLocation,
    yearDir: FileHandle,
    group: List<VisualDupeMember>,
    keepNames: Set<String>,
): MoveDupsResult {
    val extras = group.filterNot { it.name in keepNames }
    if (extras.isEmpty()) return MoveDupsResult(emptyList(), emptyList())

    val currentNames = fs.list(yearDir).map { it.name }.toSet()
    val trashFolders = TrashFolders(fs)
    val destination = trashFolders.ensure(trash, TrashAction.VISUAL_DUPES)
    val taken = trashFolders.existingNames(trash, TrashAction.VISUAL_DUPES).toMutableSet()

    val moved = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    for (extra in extras) {
        if (extra.name !in currentNames) {
            skipped += extra.name
            continue
        }
        val targetName = freeTrashName(extra.name, taken)
        fs.move(extra.handle, destination, targetName)
        taken += targetName
        moved += extra.name
    }
    return MoveDupsResult(moved, skipped)
}
