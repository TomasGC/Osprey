package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.RunRecorder
import app.osprey.history.openHistory
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val YEAR = "2021"
private const val READY = "READY"

/** The real file system, except that the second move says it is ready, then never returns: the app is "mid-run". */
private class BlocksOnSecondMove(private val inner: FileSystemRepository) : FileSystemRepository by inner {
    private var moves = 0

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        if (++moves == 2) {
            println(READY)
            System.out.flush()
            Thread.sleep(Long.MAX_VALUE)
        }
        return inner.move(entry, destFolder, newName)
    }
}

private fun JobQueue.awaitJobs() = runBlocking {
    entries.first { jobs -> jobs.isNotEmpty() && jobs.all { it.state is JobState.Finished } }
}

/**
 * A process for [ProcessKillEndToEndTest] to kill (#79): scans and applies Flatten on `args[0]`'s year with the real
 * queue, controller and file history in `args[1]`, and stops inside the second move, printing [READY] first.
 */
fun main(args: Array<String>) {
    val year = PathHandle(Path.of(args[0]).resolve(YEAR))
    val history = openHistory(File(args[1]))
    val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO), RunRecorder(history))
    val flatten = flattenController(BlocksOnSecondMove(DesktopFileSystemRepository()), jobs)
    flatten.onScan(year, YEAR)
    jobs.awaitJobs()
    flatten.onApply(year, YEAR)
    Thread.sleep(Long.MAX_VALUE)
}
