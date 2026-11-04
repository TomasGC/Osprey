package app.osprey.jobs

import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.JobHistoryStore
import app.osprey.history.RunRecord
import app.osprey.history.RunRecorder
import app.osprey.history.RunState
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val WAIT_MS = 5_000L
private val T0: Instant = Instant.parse("2026-11-02T10:00:00Z")

class JobQueueHistoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = InMemoryJobHistoryStore()
    private var tick = 0L
    private val queue = JobQueue(scope, RunRecorder(history) { T0.plusSeconds(tick++) })

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun await(handle: JobHandle): JobOutcome = runBlocking { withTimeout(WAIT_MS) { handle.await() } }

    private fun only(): RunRecord = history.runs().single()

    @Test
    fun `a job is recorded queued, started and ended with its summary`() {
        await(queue.enqueue("Reorg") { JobOutcome("Reorg: 1 done") })

        val run = only()
        assertEquals("Reorg", run.action)
        assertEquals(RunState.Done, run.state)
        assertEquals("Reorg: 1 done", run.summary)
        assertEquals(T0, run.queuedAt)
        assertEquals(T0.plusSeconds(1), run.startedAt)
        assertEquals(T0.plusSeconds(2), run.endedAt)
    }

    @Test
    fun `a job with failed files is a failed run`() {
        await(queue.enqueue("Dates") { JobOutcome("Dates: 1 done, 1 failed", failures = 1) })

        assertEquals(RunState.Failed, only().state)
    }

    @Test
    fun `a stopped job is a stopped run`() {
        await(queue.enqueue("Dates") { JobOutcome("Dates: stopped", stopped = true) })

        assertEquals(RunState.Stopped, only().state)
    }

    @Test
    fun `a job that throws is a failed run with its message`() {
        await(queue.enqueue("Rename") { error("boom") })

        val run = only()
        assertEquals(RunState.Failed, run.state)
        assertEquals("Rename failed: boom", run.summary)
    }

    @Test
    fun `a job removed before it started is a stopped run that never started`() {
        val gate = CountDownLatch(1)
        val first = queue.enqueue("Long") {
            gate.await(WAIT_MS, TimeUnit.MILLISECONDS)
            JobOutcome("Long: done")
        }
        val second = queue.enqueue("Waiting") { JobOutcome("never") }

        second.stop()
        gate.countDown()
        await(second)
        await(first)

        val run = history.runs().first { it.action == "Waiting" }
        assertEquals(RunState.Stopped, run.state)
        assertEquals("Waiting: stopped before it started", run.summary)
        assertNull(run.startedAt)
        assertNotNull(run.endedAt)
    }

    @Test
    fun `the files a job records are kept in order`() {
        val a = FileRecord("a.jpg", FileResult.Done)
        val b = FileRecord("b.jpg", FileResult.Failed, "locked")

        await(
            queue.enqueue("Rename") { control ->
                control.record(a)
                control.record(b)
                JobOutcome("Rename: 1")
            },
        )

        assertEquals(listOf(a, b), only().files)
    }

    @Test
    fun `the lines of the outcome become the log of the run`() {
        await(queue.enqueue("Dates scan") { JobOutcome("Dates scan: 2", lines = listOf("SET a", "SET b")) })

        assertEquals(listOf("SET a", "SET b"), only().log)
    }

    @Test
    fun `a history that cannot write never ends the queue`() {
        val broken = object : JobHistoryStore by InMemoryJobHistoryStore() {
            override fun begin(action: String, at: Instant): Long = error("disk full")

            override fun start(id: Long, at: Instant) = error("disk full")

            override fun appendFile(id: Long, record: FileRecord) = error("disk full")

            override fun finish(id: Long, state: RunState, summary: String, at: Instant) = error("disk full")
        }
        val brokenQueue = JobQueue(scope, RunRecorder(broken))

        val outcome = await(
            brokenQueue.enqueue("Reorg") { control ->
                control.record(FileRecord("a", FileResult.Done))
                JobOutcome("Reorg: 1 done")
            },
        )

        assertEquals("Reorg: 1 done", outcome.summary)
        assertEquals("Next: ok", await(brokenQueue.enqueue("Next") { JobOutcome("Next: ok") }).summary)
    }

    @Test
    fun `a history failing only on a later write still lets the job end`() {
        val failing = object : JobHistoryStore by InMemoryJobHistoryStore() {
            override fun appendLog(id: Long, line: String) = error("disk full")
        }
        val failingQueue = JobQueue(scope, RunRecorder(failing))

        val outcome = await(failingQueue.enqueue("Scan") { JobOutcome("Scan: 1", lines = listOf("x")) })

        assertEquals("Scan: 1", outcome.summary)
        assertEquals(RunState.Done, failing.runs().single().state)
    }

    @Test
    fun `a job sees how many of the files it recorded failed`() {
        var seen = -1
        await(
            queue.enqueue("Rename") { control ->
                control.record(FileRecord("a", FileResult.Done))
                control.record(FileRecord("b", FileResult.Failed, "locked"))
                seen = control.failedFiles
                JobOutcome("Rename: 1 done, 1 failed", failures = seen)
            },
        )

        assertEquals(1, seen)
    }
}
