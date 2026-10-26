package app.osprey.jobs

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val SPIN_MS = 5L

class JobQueueTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = JobQueue(scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun <T> waitFor(block: suspend () -> T): T = runBlocking { withTimeout(WAIT_MS) { block() } }

    private fun entriesWhere(condition: (List<JobEntry>) -> Boolean): List<JobEntry> =
        waitFor { queue.entries.first(condition) }

    private fun states() = queue.entries.value.map { it.state }

    private fun release(latch: CountDownLatch) = latch.countDown()

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(SPIN_MS)
        assertTrue(condition())
    }

    private fun hold(latch: CountDownLatch) = assertTrue(latch.await(WAIT_MS, TimeUnit.MILLISECONDS))

    @Test
    fun `a job runs and its outcome comes back to the caller`() {
        val handle = queue.enqueue("Reorg") { JobOutcome("Reorg: 1 done") }

        assertEquals(JobOutcome("Reorg: 1 done"), waitFor { handle.await() })
        val entry = entriesWhere { it.singleOrNull()?.state is JobState.Finished }.single()
        assertEquals("Reorg", entry.name)
        assertEquals(JobState.Finished(JobOutcome("Reorg: 1 done")), entry.state)
    }

    @Test
    fun `a job is running until it returns`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg") {
            hold(gate)
            JobOutcome("done")
        }

        entriesWhere { it.singleOrNull()?.state is JobState.Running }
        release(gate)
        waitFor { handle.await() }
    }

    @Test
    fun `a running job shows the file it reported`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg") { control ->
            control.report(2, 5, "b.jpg")
            hold(gate)
            JobOutcome("done")
        }

        entriesWhere { (it.singleOrNull()?.state as? JobState.Running)?.progress == JobProgress(2, 5, "b.jpg") }
        release(gate)
        waitFor { handle.await() }
    }

    @Test
    fun `a second job waits for the first and they never overlap`() {
        val gate = CountDownLatch(1)
        val running = AtomicInteger()
        val overlap = AtomicInteger()
        val order = mutableListOf<String>()
        fun work(name: String, wait: Boolean) = { _: JobControl ->
            if (running.incrementAndGet() > 1) overlap.incrementAndGet()
            if (wait) hold(gate)
            synchronized(order) { order += name }
            running.decrementAndGet()
            JobOutcome(name)
        }
        val first = queue.enqueue("A", work = work("A", wait = true))
        val second = queue.enqueue("B", work = work("B", wait = false))

        entriesWhere { it.size == 2 && it[0].state is JobState.Running }
        assertEquals(JobState.Queued, queue.entries.value[1].state)
        release(gate)
        waitFor { first.await() }
        waitFor { second.await() }

        assertEquals(listOf("A", "B"), synchronized(order) { order.toList() })
        assertEquals(0, overlap.get())
    }

    @Test
    fun `stopping a running job tells it to stop and shows that it is stopping`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg") { control ->
            while (!control.shouldStop()) Thread.sleep(SPIN_MS)
            hold(gate)
            JobOutcome("Reorg: stopped", stopped = true)
        }
        entriesWhere { it.singleOrNull()?.state is JobState.Running }

        handle.stop()

        entriesWhere { (it.singleOrNull()?.state as? JobState.Running)?.stopping == true }
        release(gate)
        assertTrue(waitFor { handle.await() }.stopped)
    }

    @Test
    fun `progress reported while stopping keeps the job marked as stopping`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg") { control ->
            while (!control.shouldStop()) Thread.sleep(SPIN_MS)
            control.report(1, 2, "b.jpg")
            hold(gate)
            JobOutcome("stopped", stopped = true)
        }
        entriesWhere { it.singleOrNull()?.state is JobState.Running }

        handle.stop()

        entriesWhere {
            val running = it.singleOrNull()?.state as? JobState.Running
            running?.stopping == true && running.progress == JobProgress(1, 2, "b.jpg")
        }
        release(gate)
        waitFor { handle.await() }
    }

    @Test
    fun `stopping a job that is still waiting removes it without running it`() {
        val gate = CountDownLatch(1)
        var ran = false
        val first = queue.enqueue("A") {
            hold(gate)
            JobOutcome("A")
        }
        val second = queue.enqueue("B") {
            ran = true
            JobOutcome("B")
        }
        entriesWhere { it.size == 2 }

        second.stop()

        assertEquals(listOf("A"), queue.entries.value.map { it.name })
        assertEquals(JobOutcome("B: stopped before it started", stopped = true), waitFor { second.await() })
        release(gate)
        waitFor { first.await() }
        assertFalse(ran)
    }

    @Test
    fun `the job after a removed one still runs`() {
        val gate = CountDownLatch(1)
        val first = queue.enqueue("A") {
            hold(gate)
            JobOutcome("A")
        }
        val removed = queue.enqueue("B") { JobOutcome("B") }
        val third = queue.enqueue("C") { JobOutcome("C") }
        entriesWhere { it.size == 3 }
        removed.stop()

        release(gate)

        assertEquals("A", waitFor { first.await() }.summary)
        assertEquals("C", waitFor { third.await() }.summary)
    }

    @Test
    fun `stopping a job that is over changes nothing`() {
        val handle = queue.enqueue("A") { JobOutcome("A") }
        waitFor { handle.await() }
        val before = queue.entries.value

        handle.stop()

        assertEquals(before, queue.entries.value)
    }

    @Test
    fun `a job that throws ends as a failure carrying the message and the queue goes on`() {
        val broken = queue.enqueue("Reorg") { error("kaboom") }
        val next = queue.enqueue("Trash") { JobOutcome("Trash: done") }

        val outcome = waitFor { broken.await() }

        assertEquals("Reorg failed: kaboom", outcome.summary)
        assertEquals(1, outcome.failures)
        assertEquals("Trash: done", waitFor { next.await() }.summary)
    }

    @Test
    fun `a job that throws without a message is named by the kind of error`() {
        val broken = queue.enqueue("Reorg") { throw IllegalStateException() }

        assertEquals("Reorg failed: IllegalStateException", waitFor { broken.await() }.summary)
    }

    @Test
    fun `only the last five finished jobs are kept`() {
        repeat(7) { index -> waitFor { queue.enqueue("Job $index") { JobOutcome("done $index") }.await() } }

        val kept = entriesWhere { entries -> entries.all { it.state is JobState.Finished } }

        assertEquals((3L..7L).toList(), kept.map { it.id })
    }

    @Test
    fun `the completion callback gets the outcome once the job is finished`() {
        val seen = mutableListOf<Pair<JobOutcome, JobState?>>()
        val handle = queue.enqueue("A", onDone = { outcome ->
            seen += outcome to queue.entries.value.singleOrNull()?.state
        }) { JobOutcome("A done") }

        waitFor { handle.await() }
        waitUntil { seen.isNotEmpty() }

        assertEquals(JobOutcome("A done"), seen.single().first)
        assertEquals(JobState.Finished(JobOutcome("A done")), seen.single().second)
    }

    @Test
    fun `the completion callback also runs for a job removed while it was waiting`() {
        val gate = CountDownLatch(1)
        val seen = mutableListOf<JobOutcome>()
        val first = queue.enqueue("A") {
            hold(gate)
            JobOutcome("A")
        }
        val second = queue.enqueue("B", onDone = { seen += it }) { JobOutcome("B") }
        entriesWhere { it.size == 2 }

        second.stop()

        assertEquals(listOf(JobOutcome("B: stopped before it started", stopped = true)), seen)
        release(gate)
        waitFor { first.await() }
    }

    @Test
    fun `a completion callback that throws does not end the queue`() {
        val broken = queue.enqueue("A", onDone = { error("watcher broke") }) { JobOutcome("A") }
        val next = queue.enqueue("B") { JobOutcome("B") }

        waitFor { broken.await() }

        assertEquals("B", waitFor { next.await() }.summary)
    }

    @Test
    fun `a job can be stopped by its id`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("A") { control ->
            while (!control.shouldStop()) Thread.sleep(SPIN_MS)
            hold(gate)
            JobOutcome("A stopped", stopped = true)
        }
        entriesWhere { it.singleOrNull()?.state is JobState.Running }

        queue.stop(handle.id)
        queue.stop(999L)

        entriesWhere { (it.singleOrNull()?.state as? JobState.Running)?.stopping == true }
        release(gate)
        assertTrue(waitFor { handle.await() }.stopped)
    }

    @Test
    fun `a job is a writer unless it says otherwise`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg apply") {
            hold(gate)
            JobOutcome("done")
        }

        assertTrue(entriesWhere { it.size == 1 }.single().writes)
        release(gate)
        waitFor { handle.await() }
    }

    @Test
    fun `a job enqueued with writes false keeps that on its entry`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("Reorg scan", writes = false) {
            hold(gate)
            JobOutcome("done")
        }

        assertFalse(entriesWhere { it.size == 1 }.single().writes)
        release(gate)
        waitFor { handle.await() }
        assertFalse(entriesWhere { it.singleOrNull()?.state is JobState.Finished }.single().writes)
    }

    @Test
    fun `dismissing a finished job removes it`() {
        val handle = queue.enqueue("A") { JobOutcome("A") }
        waitFor { handle.await() }
        entriesWhere { it.singleOrNull()?.state is JobState.Finished }

        queue.dismiss(handle.id)

        assertTrue(queue.entries.value.isEmpty())
    }

    @Test
    fun `dismissing a job that is running or unknown removes nothing`() {
        val gate = CountDownLatch(1)
        val handle = queue.enqueue("A") {
            hold(gate)
            JobOutcome("A")
        }
        entriesWhere { it.singleOrNull()?.state is JobState.Running }

        queue.dismiss(handle.id)
        queue.dismiss(999L)

        assertEquals(1, queue.entries.value.size)
        assertTrue(states().single() is JobState.Running)
        release(gate)
        waitFor { handle.await() }
    }
}
