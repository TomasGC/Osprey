package app.osprey.actions.trash

import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.RetryPlanner
import app.osprey.jobs.JobControl

private const val NAME_TAKEN = "name taken in the library: restore it from the Trash screen"

/**
 * Retries the Trash files a delete or a restore failed on (#83). A failed file is still in the Trash, so each retry
 * reads the Trash again and keeps the entries whose label (`Trash/2019/Dedupe/a.jpg`) the run recorded as failed.
 */
class TrashRetrier(private val fs: FileSystemRepository) {

    /** The entries of the Trash of [root] whose label is in [failed]. */
    fun entries(root: FileHandle, failed: Set<String>): List<TrashEntry> =
        TrashScanner(fs).scan(root).entries.filter { it.label in failed }

    /** The question to ask before deleting those files again, or null when none of them is left in the Trash. */
    fun deleteQuestion(root: FileHandle, failed: Set<String>): String? =
        entries(root, failed).takeIf { it.isNotEmpty() }?.let(::clearConfirmation)

    /** Deletes the [failed] files that are still in the Trash; reports the others as skipped. */
    fun delete(root: FileHandle, failed: Set<String>, control: JobControl): TrashClearResult {
        val found = entries(root, failed)
        skipGone(failed, found, control)
        return TrashClearer(fs).clear(root, found, control)
    }

    /**
     * Restores the [failed] files that are still in the Trash and whose name is free in the library. A file whose name
     * is now taken stays in the Trash and is reported as skipped; the others still gone are reported as skipped too.
     */
    fun restore(root: FileHandle, failed: Set<String>, control: JobControl): TrashRestoreResult {
        val found = entries(root, failed)
        skipGone(failed, found, control)
        val restorer = TrashRestorer(fs)
        val plan = restorer.plan(root, found)
        plan.conflicts.forEach { control.record(FileRecord(it.entry.label, FileResult.Skipped, NAME_TAKEN)) }
        return restorer.restore(root, RestorePlan(plan.direct, emptyList(), plan.renames), control)
    }

    private fun skipGone(failed: Set<String>, found: List<TrashEntry>, control: JobControl) {
        RetryPlanner.missingRecords(failed, found.map { it.label }).forEach(control::record)
    }
}
