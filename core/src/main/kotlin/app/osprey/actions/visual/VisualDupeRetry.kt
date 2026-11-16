package app.osprey.actions.visual

import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.RetryPlanner
import app.osprey.jobs.JobControl

/**
 * Retries the files a Visual Dupes group move failed on (#83): the user had already unticked them, so each [failed]
 * name still in [yearDir] goes to the Trash again, with no new scan. A name no longer in the folder is reported as
 * skipped.
 */
fun retryVisualDupes(
    fs: FileSystemRepository,
    trash: TrashLocation,
    yearDir: FileHandle,
    failed: Set<String>,
    control: JobControl,
): MoveDupsResult {
    val present = fs.list(yearDir)
        .filter { !it.isDirectory && it.name in failed }
        .map { VisualDupeMember(it.handle, it.name, it.sizeBytes) }
    val missing = RetryPlanner.missingRecords(failed, present.map { it.name })
    missing.forEach(control::record)
    val result = moveUncheckedToTrash(fs, trash, yearDir, present, emptySet(), control)
    return result.copy(skipped = result.skipped + missing.map { it.path })
}
