package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.JobHistoryStore
import app.osprey.history.RunRecorder
import app.osprey.history.openHistory
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.media.FakeMediaMetadataReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val WAIT_MS = 10_000L
private const val YEAR = "2021"

/** The real file system, except that moving the file called [name] fails like a locked file. */
class FailsToMove(private val inner: FileSystemRepository, private val name: String) : FileSystemRepository by inner {

    override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
        if ((entry as PathHandle).path.fileName.toString() == name) throw IOException("locked")
        return inner.move(entry, destFolder, newName)
    }
}

/**
 * One temp library with a year folder, real files on disk, and the pieces a launch of the desktop app wires together
 * around them (#79): the real file system, a real job queue recording into a real history in files. Only the media
 * reader is a stub, so a test decides what a photo's metadata says. [launch] is a new launch over the same folders.
 */
class ActionHarness(val root: Path = Files.createTempDirectory("osprey-actions-mock")) {

    val library: Path = root.resolve("library")
    val year: Path = library.resolve(YEAR)
    val historyDir = root.resolve("history").toFile()
    val reader = FakeMediaMetadataReader()
    val fs = DesktopFileSystemRepository()

    init {
        Files.createDirectories(year)
    }

    fun yearHandle() = PathHandle(year)

    /** A file under the year folder, with [content]; its parent folders are created. */
    fun file(relative: String, content: String = relative): Path {
        val path = year.resolve(relative)
        Files.createDirectories(path.parent)
        return Files.writeString(path, content)
    }

    fun launch(): Launch = Launch()

    fun delete() {
        root.toFile().walkTopDown().forEach { it.setWritable(true) }
        root.toFile().deleteRecursively()
    }

    inner class Launch {
        val history: JobHistoryStore = openHistory(historyDir)
        val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO), RunRecorder(history))

        /** Blocks until every job of this launch is over, on the queue's own state. */
        fun awaitJobs() = runBlocking {
            withTimeout(WAIT_MS) {
                jobs.entries.first { entries -> entries.isNotEmpty() && entries.all { it.state is JobState.Finished } }
            }
        }

        /** Runs a scan then an apply of [controller] on the year and waits for each. */
        fun <P> scanAndApply(controller: SimpleActionController<P>) {
            controller.onScan(yearHandle(), YEAR)
            awaitJobs()
            controller.onApply(yearHandle(), YEAR)
            awaitJobs()
        }
    }

    /** The runs a new launch reads back from the history folder, the way the History screen would. */
    fun runsAfterRestart() = openHistory(historyDir).runs()
}
