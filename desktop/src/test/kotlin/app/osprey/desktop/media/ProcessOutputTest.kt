package app.osprey.desktop.media

import app.osprey.desktop.testing.TimedTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private val AMPLE: Duration = Duration.ofSeconds(60)

/** Long enough for the stub's JVM to start and write its process id, even on a busy machine. */
private val SHORT: Duration = Duration.ofSeconds(10)
private const val END_SECONDS = 10L

/**
 * A program that behaves as its first argument says, run from source by the test's own `java` on any platform. Each
 * process that hangs first writes its id into the file named by the second argument.
 */
private val STUB = """
    import java.nio.file.*;

    public class Stub {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "print" -> print();
                case "fail" -> { print(); System.exit(1); }
                case "silent" -> { }
                case "read-input" -> { System.in.readAllBytes(); print(); }
                case "hang" -> hang(args[1]);
                case "print-hang" -> { print(); hang(args[1]); }
                case "close-hang" -> { print(); System.out.close(); new java.io.FileOutputStream(
                    java.io.FileDescriptor.out).close(); hang(args[1]); }
                case "child-hang" -> {
                    String java = ProcessHandle.current().info().command().orElseThrow();
                    new ProcessBuilder(java, args[2], "hang", args[1] + ".child").inheritIO().start();
                    hang(args[1]);
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }

        static void print() {
            System.out.print("frame");
            System.out.flush();
        }

        static void hang(String pidFile) throws Exception {
            Files.writeString(Path.of(pidFile), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(600_000);
        }
    }
""".trimIndent()

class ProcessOutputTest : TimedTest() {

    private lateinit var tempDir: Path
    private lateinit var stub: Path
    private lateinit var pidFile: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-process-output-test")
        stub = Files.writeString(tempDir.resolve("Stub.java"), STUB)
        pidFile = tempDir.resolve("pid")
    }

    @After
    fun tearDown() {
        // A test that fails must not leave its stubs running.
        listOf(pidFile, childPidFile()).forEach { file -> processIn(file)?.destroyForcibly() }
        tempDir.toFile().deleteRecursively()
    }

    private fun childPidFile(): Path = tempDir.resolve("pid.child")

    private fun processIn(file: Path): ProcessHandle? =
        if (Files.exists(file)) ProcessHandle.of(Files.readString(file).toLong()).orElse(null) else null

    private fun command(mode: String): List<String> {
        val java = ProcessHandle.current().info().command().get()
        return listOf(java, stub.toString(), mode, pidFile.toString(), stub.toString())
    }

    private fun assertEnded(file: Path) {
        assertEquals("the stub never wrote $file", true, Files.exists(file))
        val process = processIn(file) ?: return
        process.onExit().get(END_SECONDS, TimeUnit.SECONDS)
        assertFalse(process.isAlive)
    }

    @Test
    fun `the output of a program that succeeds is returned`() {
        assertEquals("frame", processOutput(command("print"), AMPLE)?.toString(Charsets.UTF_8))
    }

    @Test
    fun `a program that fails gives nothing, even after printing`() {
        assertNull(processOutput(command("fail"), AMPLE))
    }

    @Test
    fun `a program that succeeds without printing gives nothing`() {
        assertNull(processOutput(command("silent"), AMPLE))
    }

    @Test
    fun `a program that reads its input finds it ended`() {
        assertEquals("frame", processOutput(command("read-input"), SHORT)?.toString(Charsets.UTF_8))
    }

    @Test
    fun `a program that is not installed gives nothing`() {
        assertNull(processOutput(listOf("osprey-no-such-program"), AMPLE))
    }

    @Test
    fun `a program that hangs is destroyed at the timeout`() {
        assertNull(processOutput(command("hang"), SHORT))
        assertEnded(pidFile)
    }

    @Test
    fun `a program that prints and then hangs gives nothing and is destroyed`() {
        assertNull(processOutput(command("print-hang"), SHORT))
        assertEnded(pidFile)
    }

    @Test
    fun `a program that closes its output and hangs is destroyed at the timeout`() {
        assertNull(processOutput(command("close-hang"), SHORT))
        assertEnded(pidFile)
    }

    @Test
    fun `what a hung program started is destroyed with it`() {
        assertNull(processOutput(command("child-hang"), SHORT))
        assertEnded(pidFile)
        assertEnded(childPidFile())
    }

    @Test
    fun `the output is read even when the common pool is fully busy`() {
        val release = CountDownLatch(1)
        val pool = ForkJoinPool.commonPool()
        val busy = CountDownLatch(pool.parallelism)
        repeat(pool.parallelism) {
            pool.execute {
                busy.countDown()
                release.await()
            }
        }
        try {
            assertEquals(true, busy.await(END_SECONDS, TimeUnit.SECONDS))

            assertEquals("frame", processOutput(command("print"), SHORT)?.toString(Charsets.UTF_8))
        } finally {
            release.countDown()
        }
    }
}
