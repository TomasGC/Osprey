package app.osprey.desktop.media

import java.io.IOException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val READER_NAME = "osprey-process-output"

/**
 * Stdout of [command] when it exits with 0 and wrote something within [timeout], else null. The output is read on a
 * thread of its own, so a hung program cannot block the caller and concurrent calls never queue behind each other.
 * A program still running at the end is destroyed, with everything it started.
 */
@Suppress("SwallowedException") // a missing binary means "nothing", never a crash
internal fun processOutput(command: List<String>, timeout: Duration): ByteArray? {
    val process = try {
        ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (e: IOException) {
        return null
    }
    return try {
        process.outputStream.close()
        collect(process, timeout)
    } finally {
        if (process.isAlive) destroyTree(process)
    }
}

@Suppress("SwallowedException") // a timeout or a broken pipe means "nothing"
private fun collect(process: Process, timeout: Duration): ByteArray? {
    val deadline = System.nanoTime() + timeout.toNanos()
    val output = FutureTask { process.inputStream.readBytes() }
    Thread(output, READER_NAME).apply { isDaemon = true }.start()
    return try {
        val bytes = output.get(timeout.toNanos(), TimeUnit.NANOSECONDS)
        val ended = process.waitFor(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        bytes.takeIf { ended && process.exitValue() == 0 && it.isNotEmpty() }
    } catch (e: TimeoutException) {
        null
    } catch (e: ExecutionException) {
        null
    }
}

/** The children first: once the parent is gone they can no longer be found, and they hold its output open. */
private fun destroyTree(process: Process) {
    process.descendants().forEach { it.destroyForcibly() }
    process.destroyForcibly()
}
