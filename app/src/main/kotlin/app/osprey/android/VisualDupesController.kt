package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.visual.MoveDupsResult
import app.osprey.actions.visual.VisualDupePlanner
import app.osprey.actions.visual.moveUncheckedToTrash
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.withStoppedNote
import app.osprey.media.MediaMetadataReader

/**
 * Visual Dupes: the scan, and "move unchecked" for one group, both as jobs of [jobs]. The groups stay in the
 * controller, so switching screens does not lose a scan that took minutes. They belong to the folder they were
 * scanned in: [groupsIn] hands them out for that folder only, so a card can never move files of another year.
 */
class VisualDupesController(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
    private val jobs: JobQueue,
    private val trashLocation: () -> TrashLocation,
) {
    private var scannedDir by mutableStateOf<FileHandle?>(null)
    private var groups by mutableStateOf(emptyList<VisualDupeGroupUi>())

    var scanning by mutableStateOf(false)
        private set

    /** The groups found in [yearDir] by the last scan, or none when that scan was of another folder. */
    fun groupsIn(yearDir: FileHandle): List<VisualDupeGroupUi> = if (scannedDir == yearDir) groups else emptyList()

    fun onScan(yearDir: FileHandle) {
        if (scanning) return
        scanning = true
        jobs.enqueue("Visual Dupes scan", onDone = { scanning = false }) { control ->
            val plan = VisualDupePlanner(fs, mediaMetadataReader).plan(yearDir, control = control)
            if (control.shouldStop()) {
                JobOutcome("Visual Dupes scan: stopped", stopped = true)
            } else {
                groups = toUiGroups(plan.groups)
                scannedDir = yearDir
                JobOutcome("Visual Dupes scan: ${plan.groups.size} groups")
            }
        }
    }

    /** Moves the members of [group] that are not ticked to the Trash, once at least one but not all are ticked. */
    fun moveUnchecked(yearDir: FileHandle, group: VisualDupeGroupUi) {
        val kept = group.members.filter { group.keep[it.name] == true }.map { it.name }.toSet()
        when {
            group.moving || group.done -> Unit
            kept.isEmpty() -> group.status = "Keep at least one image."
            kept.size == group.members.size -> group.status = "Nothing unchecked -- nothing to move."
            else -> {
                group.moving = true
                val trash = trashLocation()
                jobs.enqueue("Visual Dupes group #${group.index}", onDone = { group.moving = false }) { control ->
                    val result = moveUncheckedToTrash(fs, trash, yearDir, group.members, kept, control)
                    val stopped = control.shouldStop()
                    group.done = !stopped
                    group.status = moveStatus(result, stopped)
                    val summary = "Visual Dupes group #${group.index}: moved ${result.moved.size}"
                    JobOutcome(withStoppedNote(summary, stopped), stopped = stopped)
                }
            }
        }
    }

    private fun moveStatus(result: MoveDupsResult, stopped: Boolean): String {
        val skipped = result.skipped.takeIf { it.isNotEmpty() }?.let { " (skipped: ${it.joinToString(", ")})" }
        return (if (stopped) "Stopped after moving " else "Moved ") + result.moved.size + skipped.orEmpty()
    }
}
