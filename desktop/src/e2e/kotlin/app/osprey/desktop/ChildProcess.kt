package app.osprey.desktop

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

const val START_TIMEOUT_SECONDS = 90L
private const val READ_END_SECONDS = 10L

/** The command that runs [mainClass] (a `main` of the e2e source set) in a new JVM with the e2e classpath. */
fun appCommand(mainClass: String, vararg args: String): List<String> = listOf(
    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
    "-cp",
    System.getProperty("osprey.e2e.classpath"),
    mainClass,
    *args,
)

/**
 * Starts [command] and returns once the child prints READY. Its output goes to [output], so a failure can show it. A
 * child that does not get there within [timeoutSeconds], or ends without it, is destroyed, so it cannot outlive the
 * test, and the start fails with an [AssertionError] that holds what the child said, whether it hung or ended.
 * [onStart] sees the process before the wait, since a failed start returns none (#124).
 */
fun startUntilReady(
    command: List<String>,
    stage: String,
    output: MutableList<String> = mutableListOf(),
    timeoutSeconds: Long = START_TIMEOUT_SECONDS,
    onStart: (Process) -> Unit = {},
): Process {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    onStart(process)
    val ready = CompletableFuture.supplyAsync {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.onEach { output += it }.any { it == "READY" }
        }
    }
    var gotThere = false
    val failure = runCatching { gotThere = ready.get(timeoutSeconds, TimeUnit.SECONDS) }.exceptionOrNull()
    if (gotThere) return process
    process.destroyForcibly().waitFor()
    // The read ends once the child is gone, so what it said is all in [output] by the time the message is built.
    runCatching { ready.get(READ_END_SECONDS, TimeUnit.SECONDS) }
    val after = if (failure is TimeoutException) " in $timeoutSeconds s" else ""
    throw AssertionError("the app process never got $stage$after:\n${output.joinToString("\n")}", failure)
}
