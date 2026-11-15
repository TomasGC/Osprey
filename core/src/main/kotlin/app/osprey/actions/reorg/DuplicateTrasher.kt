package app.osprey.actions.reorg

import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashFolders
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.freeTrashName
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.failureReason
import app.osprey.jobs.JobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.jobs.recordResult

/** What [DuplicateTrasher.trash] did: files [trashed], the [failures], and whether it ran to the end. */
internal class TrashOutcome(val trashed: Int, val failures: List<ReorgFailure>, val finished: Boolean)

/**
 * Sends the unticked side of each duplicate pair to `Trash/<year>/Reorg` (#91). A library file is moved; an input file
 * is copied, verified and deleted, because it may live on another provider. A file that fails stays where it was.
 */
internal class DuplicateTrasher(private val fs: FileSystemRepository, private val output: FileHandle) {

    private val folders = TrashFolders(fs)

    /** One file to set aside; [fallbackYear] is the other side's year, for a file whose own folder is no year. */
    private class Job(val side: DuplicateSide, val fallbackYear: String, val inLibrary: Boolean)

    /**
     * Trashes every unticked side of [choices]. [droppedRefiles] are the library files whose planned refile was
     * dropped, so their record says so. [control] is asked before each file.
     */
    fun trash(choices: List<DuplicateChoice>, droppedRefiles: Set<FileHandle>, control: JobControl): TrashOutcome {
        val jobs = choices.flatMap { choice ->
            val pair = choice.duplicate
            listOfNotNull(
                Job(pair.input, pair.library.year, inLibrary = false).takeUnless { choice.keepInput },
                Job(pair.library, pair.input.year, inLibrary = true).takeUnless { choice.keepLibrary },
            )
        }
        val failures = mutableListOf<ReorgFailure>()
        var trashed = 0
        val finished = control.forEachUntilStopped(jobs, { it.side.label }) { job ->
            var destination = ""
            val failure = failureReason { destination = setAside(job) }
            val note = if (job.side.handle in droppedRefiles) " (its refile was dropped)" else ""
            control.recordResult(job.side.label, failure, "moved to $destination$note")
            if (failure == null) trashed++ else failures += ReorgFailure(job.side.label, failure)
        }
        return TrashOutcome(trashed, failures, finished)
    }

    /** Moves the file into the Trash and returns where it went, like `Trash/2018/Reorg/y.jpg`. */
    private fun setAside(job: Job): String {
        val year = listOf(job.side.year, job.fallbackYear).firstOrNull(::isYear) ?: error("no year to file it under")
        val location = TrashLocation(output, year)
        val folder = folders.ensure(location, TrashAction.REORG)
        val name = freeTrashName(job.side.name, folders.existingNames(location, TrashAction.REORG))
        if (job.inLibrary) {
            fs.move(job.side.handle, folder, name)
        } else {
            fs.moveVerified(job.side.handle, job.side.sizeBytes, folder, name)
        }
        return "Trash/$year/${TrashAction.REORG.folderName}/$name"
    }

    private fun isYear(name: String): Boolean = name.isNotEmpty() && name.all(Char::isDigit)
}
