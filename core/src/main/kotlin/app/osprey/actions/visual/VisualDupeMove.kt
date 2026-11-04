package app.osprey.actions.visual

import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.freeTrashName
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.jobs.tryFile

/**
 * Moves every member of [group] not in [keepNames] into [trash]'s VisualDupes folder --
 * [VisualDupePlanner] has no `apply()` on purpose (review-before-apply lives entirely in the
 * UI), so this is where "move unchecked" actually happens, matching the POC's `move_dups`.
 * A member whose file no longer exists in [yearDir] is skipped rather than failing the whole
 * group, and one that cannot be moved is recorded as failed while the others still move; a name
 * collision in the trash gets a `__dupN` suffix, same as Dedupe.
 */
@Suppress("LongParameterList") // One call carries the whole context a group card has; a holder would only rename it.
fun moveUncheckedToTrash(
    fs: FileSystemRepository,
    trash: TrashLocation,
    yearDir: FileHandle,
    group: List<VisualDupeMember>,
    keepNames: Set<String>,
    control: JobControl = NoJobControl,
): MoveDupsResult {
    val extras = group.filterNot { it.name in keepNames }
    if (extras.isEmpty()) return MoveDupsResult(emptyList(), emptyList())

    val currentNames = fs.list(yearDir).map { it.name }.toSet()
    val trashFolders = TrashFolders(fs)
    val destination = trashFolders.ensure(trash, TrashAction.VISUAL_DUPES)
    val taken = trashFolders.existingNames(trash, TrashAction.VISUAL_DUPES).toMutableSet()

    val moved = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    val failed = mutableListOf<String>()
    control.forEachUntilStopped(extras, VisualDupeMember::name) { extra ->
        if (extra.name !in currentNames) {
            skipped += extra.name
            control.record(FileRecord(extra.name, FileResult.Skipped, "no longer in the folder"))
        } else {
            val targetName = freeTrashName(extra.name, taken)
            val where = "${TrashAction.VISUAL_DUPES.folderName}/$targetName"
            val worked = control.tryFile(extra.name, "moved to Trash/${trash.year}/$where") {
                fs.move(extra.handle, destination, targetName)
            }
            if (worked) {
                taken += targetName
                moved += extra.name
            } else {
                failed += extra.name
            }
        }
    }
    return MoveDupsResult(moved, skipped, failed)
}
