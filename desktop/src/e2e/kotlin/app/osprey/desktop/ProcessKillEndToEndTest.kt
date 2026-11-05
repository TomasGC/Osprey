package app.osprey.desktop

import app.osprey.desktop.testing.TimedTest
import app.osprey.history.FileResult
import app.osprey.history.RunState
import app.osprey.history.openHistory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val START_TIMEOUT_SECONDS = 90L

/**
 * A real process killed in the middle of a run (#79): the History written to disk by a child JVM must show the run as
 * Interrupted when the next launch opens it, with the file the run had finished and none it had not.
 */
class ProcessKillEndToEndTest : TimedTest() {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val year get() = library.resolve("2021")
    private val historyDir get() = root.resolve("history")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-kill-e2e")
        Files.createDirectories(year.resolve("sub"))
        listOf("a", "b", "c").forEach { Files.writeString(year.resolve("sub").resolve("$it.jpg"), "photo $it") }
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    /** Starts the app process and returns once it is inside its second move; its output is kept for a failure. */
    private fun startAndWaitUntilMidRun(output: MutableList<String>): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(
            java, "-cp", System.getProperty("osprey.e2e.classpath"), "app.osprey.desktop.KilledMidRunKt",
            library.toString(), historyDir.toString(),
        ).redirectErrorStream(true).start()
        val ready = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.onEach { output += it }.any { it == "READY" }
            }
        }
        try {
            assertTrue("the app process never got mid-run:\n${output.joinToString("\n")}",
                ready.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }
        return process
    }

    @Test
    fun `a run the app process is killed in is Interrupted after a restart and keeps the file it had finished`() {
        val output = mutableListOf<String>()
        val process = startAndWaitUntilMidRun(output)

        process.destroyForcibly().waitFor()
        val runs = openHistory(historyDir.toFile()).runs()

        assertEquals(RunState.Done, runs.single { it.action == "Flatten 2021 scan" }.state)
        val interrupted = runs.single { it.action == "Flatten 2021 apply" }
        assertEquals(RunState.Interrupted, interrupted.state)
        assertNotNull("the run had started before it was killed", interrupted.startedAt)
        val finished = interrupted.files.single()
        assertEquals(FileResult.Done, finished.result)
        val moved = finished.path.substringAfterLast('/')
        assertTrue(Files.exists(year.resolve(moved)))
        assertEquals(2, Files.list(year.resolve("sub")).use { it.count() }.toInt())
    }
}
