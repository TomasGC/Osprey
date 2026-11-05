package app.osprey.desktop.testing

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runners.model.TestTimedOutException

private const val SLOW_MS = 10_000L
private const val LIMIT_MS = 300L
private const val SOON_MS = 5_000L

class SameThreadTimeoutTest : TimedTest() {

    private val description = Description.createTestDescription(SameThreadTimeoutTest::class.java, "demo")

    private fun runWithLimit(limitMs: Long, body: () -> Unit) =
        SameThreadTimeout(limitMs, TimeUnit.MILLISECONDS)
            .apply(object : Statement() { override fun evaluate() = body() }, description)
            .evaluate()

    @Test
    fun `a test that ends in time passes and runs on the calling thread`() {
        val caller = Thread.currentThread()
        var ranOn: Thread? = null
        runWithLimit(SOON_MS) { ranOn = Thread.currentThread() }
        assertSame(caller, ranOn)
    }

    @Test
    fun `a test that waits past the limit fails as timed out, soon after the limit`() {
        val start = System.nanoTime()
        val failure = assertThrows(TestTimedOutException::class.java) { runWithLimit(LIMIT_MS) { Thread.sleep(SLOW_MS) } }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < SOON_MS)
        assertTrue(failure.message!!.contains("$LIMIT_MS milliseconds"))
    }

    @Test
    fun `the failure keeps the stack where the test was stuck`() {
        val failure = assertThrows(TestTimedOutException::class.java) { runWithLimit(LIMIT_MS) { Thread.sleep(SLOW_MS) } }
        assertTrue(failure.stackTrace.any { it.methodName == "sleep" })
    }

    @Test
    fun `a test that fails by itself keeps its own failure`() {
        val own = AssertionError("its own")
        val failure = assertThrows(AssertionError::class.java) { runWithLimit(SOON_MS) { throw own } }
        assertSame(own, failure)
    }

    @Test
    fun `a test that swallows the interrupt and ends late still fails as timed out`() {
        assertThrows(TestTimedOutException::class.java) {
            runWithLimit(LIMIT_MS) { try { Thread.sleep(SLOW_MS / 5) } catch (_: InterruptedException) { } }
        }
    }

    @Test
    fun `a test that ignores the interrupt is given up on after the grace time, by name`() {
        val given = AtomicReference<Description?>()
        val rule = SameThreadTimeout(LIMIT_MS, TimeUnit.MILLISECONDS, graceSeconds = 1) { d, _ -> given.set(d) }
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        assertThrows(TestTimedOutException::class.java) {
            rule.apply(object : Statement() {
                override fun evaluate() {
                    while (given.get() == null && System.nanoTime() < end) Thread.onSpinWait()
                }
            }, description).evaluate()
        }
        assertSame(description, given.get())
    }

    @Test
    fun `a test that ended after its timeout is not given up on`() {
        val given = AtomicReference<Description?>()
        val rule = SameThreadTimeout(LIMIT_MS, TimeUnit.MILLISECONDS, graceSeconds = 2) { d, _ -> given.set(d) }
        assertThrows(TestTimedOutException::class.java) {
            rule.apply(object : Statement() { override fun evaluate() = Thread.sleep(SLOW_MS) }, description).evaluate()
        }
        Thread.sleep(2_500)
        assertNull(given.get())
    }

    @Test
    fun `TimedTest takes its limit from its argument, 120 s by default`() {
        assertEquals(7_000L, TimedTest(limitSeconds = 7).timeout.limitMillis)
        assertEquals(120_000L, TimedTest().timeout.limitMillis)
    }

    @Test
    fun `the interrupt of a timeout does not reach the next test`() {
        assertThrows(TestTimedOutException::class.java) {
            runWithLimit(LIMIT_MS) { while (!Thread.currentThread().isInterrupted) Thread.onSpinWait() }
        }
        assertFalse(Thread.currentThread().isInterrupted)
    }
}
