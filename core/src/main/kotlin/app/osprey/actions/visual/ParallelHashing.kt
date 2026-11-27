package app.osprey.actions.visual

import app.osprey.filesystem.FileEntry
import app.osprey.jobs.JobControl
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorCompletionService

/** One run of [hashFrames] over [pool], driven from the calling thread: the only one that touches this state. */
internal class ParallelHashing(
    private val files: List<FileEntry>,
    private val workers: Int,
    private val control: JobControl,
    private val hash: (FileEntry) -> Long?,
    pool: Executor,
) {
    private val running = ExecutorCompletionService<Pair<Int, Result<Long?>>>(pool)
    private val results = mutableMapOf<Int, Result<Long?>>()
    private var started = 0
    private var halted = false
    private val inFlight get() = started - results.size

    fun run(): List<HashedFile>? {
        startWhileRoom()
        while (inFlight > 0) {
            takeOne()
            startWhileRoom()
        }
        val hashed = (0 until started).mapNotNull { index ->
            results.getValue(index).getOrThrow()?.let { HashedFile(files[index], it) }
        }
        return hashed.takeIf { started == files.size }
    }

    private fun startWhileRoom() {
        while (!halted && started < files.size && inFlight < workers) startNext()
    }

    private fun startNext() {
        halted = control.shouldStop()
        if (halted) return
        val index = started++
        control.report(results.size, files.size, files[index].name)
        running.submit { index to runCatching { hash(files[index]) } }
    }

    private fun takeOne() {
        val (index, result) = running.take().get()
        results[index] = result
        halted = halted || result.isFailure
    }
}
