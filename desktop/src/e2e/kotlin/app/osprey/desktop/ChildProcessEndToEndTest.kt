package app.osprey.desktop

import app.osprey.desktop.testing.TimedTest
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHILD = "app.osprey.desktop.StubChildKt"
private const val SHORT_TIMEOUT_SECONDS = 2L
private const val WELL_UNDER_THE_DEFAULT_SECONDS = 20L
private const val DEATH_WAIT_SECONDS = 10L

/**
 * The start helper of the kill tests (#124), on the path they never take: a child that does not get to READY must not
 * outlive the test, whether it hangs or ends, and only a line that is READY counts.
 */
class ChildProcessEndToEndTest : TimedTest() {

    private val started = mutableListOf<Process>()

    /** A safety net: whatever a failing test leaves running is killed, so a bug here cannot leak a JVM. */
    @After
    fun tearDown() {
        started.forEach { it.destroyForcibly() }
    }

    @Test
    fun `a child that says READY is returned while it runs`() {
        val process = startUntilReady(appCommand(CHILD, "ready"), "ready", onStart = { started += it })

        assertTrue("the child is not running", process.isAlive)
    }

    @Test
    fun `a child that says READY after other lines is returned while it runs`() {
        val process = startUntilReady(appCommand(CHILD, "late"), "ready", onStart = { started += it })

        assertTrue("the child is not running", process.isAlive)
    }

    @Test
    fun `a child that never says READY is destroyed when the timeout passes and the start fails with what it said`() {
        val begun = System.nanoTime()

        val failure = assertThrows(AssertionError::class.java) {
            startUntilReady(
                appCommand(CHILD, "hang"),
                "ready",
                timeoutSeconds = SHORT_TIMEOUT_SECONDS,
                onStart = { started += it },
            )
        }

        val seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begun)
        assertTrue("the start waited $seconds s: the timeout was not used", seconds < WELL_UNDER_THE_DEFAULT_SECONDS)
        val message = failure.message!!
        assertTrue("the message lacks the timeout: $message", message.contains("in $SHORT_TIMEOUT_SECONDS s"))
        assertTrue("the message lacks what the child said: $message", message.contains("starting"))
        assertTrue("the timeout is not the cause: ${failure.cause}", failure.cause is TimeoutException)
        val dead = started.single().waitFor(DEATH_WAIT_SECONDS, TimeUnit.SECONDS)
        assertTrue("the child is still running after the failed start", dead)
    }

    @Test
    fun `a line that only starts with READY is not READY`() {
        val failure = assertThrows(AssertionError::class.java) {
            startUntilReady(
                appCommand(CHILD, "almost"),
                "ready",
                timeoutSeconds = SHORT_TIMEOUT_SECONDS,
                onStart = { started += it },
            )
        }

        assertTrue("the message lacks the child's line: ${failure.message}", failure.message!!.contains("READY now"))
        assertTrue("the child is still running", started.single().waitFor(DEATH_WAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a child that ends without READY fails the start and its output is in the message`() {
        val failure = assertThrows(AssertionError::class.java) {
            startUntilReady(appCommand(CHILD, "exit"), "ready", onStart = { started += it })
        }

        assertTrue("the message lacks the child's output: ${failure.message}", failure.message!!.contains("goodbye"))
        assertTrue("the child is still running", started.single().waitFor(DEATH_WAIT_SECONDS, TimeUnit.SECONDS))
    }
}
