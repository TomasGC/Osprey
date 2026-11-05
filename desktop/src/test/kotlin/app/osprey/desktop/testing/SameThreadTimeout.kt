package app.osprey.desktop.testing

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runners.model.TestTimedOutException

private const val RUNNING = 0
private const val DONE = 1
private const val FIRED = 2
private const val GRACE_SECONDS = 10L
private const val HALT_STATUS = 70

private val watchdogs: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "test-watchdog").apply { isDaemon = true }
}

/**
 * A time limit for one test that, unlike JUnit's `Timeout`, leaves the test on the thread that runs it (#89).
 * `Timeout` starts every test in a new thread group; AWT gives each group its own event queue and event thread, and
 * a Compose test then hangs for good when that event thread is gone while it posts to it. Here a watchdog
 * interrupts the test thread at the limit, and the rule fails the test as timed out with the stack it was stuck in.
 * A test that ignores the interrupt for [GRACE_SECONDS] more is named on stderr and the worker is halted, so a
 * hang can never block a run.
 */
class SameThreadTimeout(
    private val limit: Long,
    private val unit: TimeUnit,
    private val graceSeconds: Long = GRACE_SECONDS,
    private val onGiveUp: (Description, Array<StackTraceElement>) -> Unit = ::halt,
) : TestRule {

    val limitMillis: Long get() = unit.toMillis(limit)

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val testThread = Thread.currentThread()
            val state = AtomicInteger(RUNNING)
            val stuckAt = AtomicReference<Array<StackTraceElement>>(emptyArray())
            val delivered = CountDownLatch(1)
            val watchdog = watchdogs.schedule({
                if (state.compareAndSet(RUNNING, FIRED)) {
                    stuckAt.set(testThread.stackTrace)
                    testThread.interrupt()
                    delivered.countDown()
                    watchdogs.schedule({
                        if (state.get() == FIRED) onGiveUp(description, stuckAt.get())
                    }, graceSeconds, TimeUnit.SECONDS)
                }
            }, limit, unit)
            var failure: Throwable? = null
            try {
                base.evaluate()
            } catch (t: Throwable) {
                failure = t
            } finally {
                watchdog.cancel(false)
            }
            if (state.compareAndSet(RUNNING, DONE)) {
                failure?.let { throw it }
                return
            }
            delivered.awaitUninterruptibly()
            state.set(DONE)
            throw TestTimedOutException(limit, unit).apply {
                stackTrace = stuckAt.get()
                failure?.let { addSuppressed(it) }
            }
        }
    }
}

/**
 * Waits for the watchdog's interrupt to have been delivered, so it cannot reach the next test. `await` throws when the
 * interrupt flag is set and clears it as it does: that is how the timeout's own interrupt is cleared from the thread.
 */
private fun CountDownLatch.awaitUninterruptibly() {
    while (true) {
        try {
            await()
            return
        } catch (_: InterruptedException) {
            // the timeout's own interrupt, now cleared: wait again until the watchdog has counted down
        }
    }
}

private fun halt(description: Description, stuckAt: Array<StackTraceElement>) {
    System.err.println("${description.displayName} ignored the interrupt for $GRACE_SECONDS s after its limit; halting")
    stuckAt.forEach { System.err.println("\tat $it") }
    Runtime.getRuntime().halt(HALT_STATUS)
}
