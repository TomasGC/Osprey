package app.osprey.desktop

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The line a child prints once it is at the point where the test wants to kill it. */
const val READY = "READY"

private const val START_TIMEOUT_SECONDS = 90L
private const val READ_END_SECONDS = 10L

/** The command that runs [mainClass] (a `main` of the e2e source set) in a new JVM with the e2e classpath. */
fun appCommand(mainClass: String, vararg args: String): List<String> = listOf(
    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
    "-cp",
    System.getProperty("osprey.e2e.classpath"),
    mainClass,
    *args,
)

/** The child's side of the handshake: says [line], then blocks for good, so the parent decides what happens next. */
fun blockAfterSaying(line: String) {
    println(line)
    System.out.flush()
    Thread.sleep(Long.MAX_VALUE)
}

/**
 * Starts [command] and returns once the child prints [READY] on a line of its own. A child that does not get there
 * within [timeoutSeconds], or ends without it, is destroyed, so it cannot outlive the test, and the start fails with an
 * [AssertionError] that holds the stage, the timeout and what the child said, whether it hung or ended. [onStart] sees
 * the process before the wait, since a failed start returns none; [onLine] sees each line the child prints, before it
 * is kept, which a test uses to slow the reader down (#124).
 */
fun startUntilReady(
    command: List<String>,
    stage: String,
    timeoutSeconds: Long = START_TIMEOUT_SECONDS,
    onStart: (Process) -> Unit = {},
    onLine: (String) -> Unit = {},
): Process {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    onStart(process)
    val output = CopyOnWriteArrayList<String>()
    val ready = CompletableFuture.supplyAsync {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.onEach { line ->
                onLine(line)
                output += line
            }.any { it == READY }
        }
    }
    val outcome = runCatching { ready.get(timeoutSeconds, TimeUnit.SECONDS) }
    if (outcome.getOrNull() == true) return process
    process.destroyForcibly().waitFor()
    // The read ends once the child is gone, so what it said is all in [output] by the time the message is built.
    runCatching { ready.get(READ_END_SECONDS, TimeUnit.SECONDS) }
    val failure = outcome.exceptionOrNull()
    val after = if (failure is TimeoutException) " in $timeoutSeconds s" else ""
    throw AssertionError("the app process never got $stage$after:\n${output.joinToString("\n")}", failure)
}
