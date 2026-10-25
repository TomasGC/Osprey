package app.osprey.android

import app.osprey.filesystem.FileHandle
import app.osprey.jobs.JobControl
import app.osprey.jobs.JobState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared no-op [FileHandle] stub, reused by other desktop unit tests that don't touch the filesystem. */
object FakeHandle : FileHandle

class SimpleActionControllerTest {

    private val jobs = immediateJobQueue()

    private fun controller(
        scan: (FileHandle, JobControl) -> ScanResult<Int> = { _, _ -> ScanResult(5, "count=5", listOf("a"), 5) },
        apply: (FileHandle, Int, JobControl) -> Int = { _, plan, _ -> plan },
        onApplied: () -> Unit = {},
    ) = SimpleActionController("Test", jobs, scan, apply, onApplied)

    @Test
    fun `apply before any scan does nothing`() {
        var applyCalls = 0
        val controller = controller(apply = { _, plan, _ -> applyCalls++; plan })

        controller.onApply(FakeHandle)

        assertEquals(0, applyCalls)
        assertFalse(controller.state.applyEnabled)
        assertTrue(jobs.entries.value.isEmpty())
    }

    @Test
    fun `scan populates state and enables apply when the plan is non-empty`() {
        val controller = controller(scan = { _, _ -> ScanResult(5, "count=5", listOf("a", "b"), 5) })

        controller.onScan(FakeHandle)

        assertEquals("count=5", controller.state.summary)
        assertEquals(listOf("a", "b"), controller.state.lines)
        assertTrue(controller.state.applyEnabled)
        assertFalse(controller.state.busy)
    }

    @Test
    fun `scan with an empty plan leaves apply disabled`() {
        val controller = controller(scan = { _, _ -> ScanResult(0, "count=0", emptyList(), 0) })

        controller.onScan(FakeHandle)

        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `apply consumes the cached plan exactly once, never re-scanning`() {
        var scanCalls = 0
        var applyCalls = 0
        val controller = controller(
            scan = { _, _ -> scanCalls++; ScanResult(5, "count=5", emptyList(), 5) },
            apply = { _, plan, _ -> applyCalls++; plan },
        )

        controller.onScan(FakeHandle)
        controller.onApply(FakeHandle)
        controller.onApply(FakeHandle) // No new scan in between: must be a no-op.

        assertEquals(1, scanCalls)
        assertEquals(1, applyCalls)
        assertFalse(controller.state.applyEnabled)
        assertEquals("  -- APPLIED (5)", controller.state.appliedSuffix)
    }

    @Test
    fun `a fresh scan clears the previous applied suffix`() {
        val controller = controller()

        controller.onScan(FakeHandle)
        controller.onApply(FakeHandle)
        controller.onScan(FakeHandle)

        assertEquals("", controller.state.appliedSuffix)
    }

    @Test
    fun `each run is a job named after the action and its summary says what happened`() {
        val controller = controller()

        controller.onScan(FakeHandle)
        controller.onApply(FakeHandle)

        val finished = jobs.entries.value.map { it.name to (it.state as JobState.Finished).outcome.summary }
        assertEquals(listOf("Test scan" to "Test scan: count=5", "Test apply" to "Test apply: 5 done"), finished)
    }

    @Test
    fun `a stopped scan keeps no plan and says so`() {
        val controller = controller(scan = { _, control -> stopAsked(control); ScanResult(5, "count=5", listOf("a"), 5) })

        controller.onScan(FakeHandle)
        controller.onApply(FakeHandle)

        assertEquals("Scan stopped", controller.state.summary)
        assertEquals(emptyList<String>(), controller.state.lines)
        assertFalse(controller.state.applyEnabled)
        assertEquals("", controller.state.appliedSuffix)
    }

    @Test
    fun `a stopped apply is marked as stopped`() {
        val controller = controller(apply = { _, _, control -> stopAsked(control); 2 })

        controller.onScan(FakeHandle)
        controller.onApply(FakeHandle)

        assertEquals("  -- APPLIED (2) -- STOPPED", controller.state.appliedSuffix)
        val applyJob = jobs.entries.value.last().state as JobState.Finished
        assertEquals("Test apply: 2 done (stopped)", applyJob.outcome.summary)
    }

    @Test
    fun `the applied hook runs once an apply is over, and not after a scan`() {
        var applied = 0
        val controller = controller(onApplied = { applied++ })

        controller.onScan(FakeHandle)
        assertEquals(0, applied)
        controller.onApply(FakeHandle)

        assertEquals(1, applied)
    }

    @Test
    fun `a job that fails frees the buttons again`() {
        val controller = controller(scan = { _, _ -> error("disk gone") })

        controller.onScan(FakeHandle)

        assertFalse(controller.state.busy)
        assertEquals(1, jobs.entries.value.count { (it.state as? JobState.Finished)?.outcome?.failures == 1 })
    }

    @Test
    fun `a scan asked for while one is queued or running is not queued twice`() {
        val real = app.osprey.jobs.JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val gate = CountDownLatch(1)
        try {
            val controller = SimpleActionController<Int>(
                "Test", real,
                scan = { _, _ -> gate.await(WAIT_SECONDS, TimeUnit.SECONDS); ScanResult(1, "count=1", emptyList(), 1) },
                apply = { _, plan, _ -> plan },
            )

            controller.onScan(FakeHandle)
            controller.onScan(FakeHandle)

            assertTrue(controller.state.busy)
            assertEquals(1, real.entries.value.size)
            gate.countDown()
            runBlocking { withTimeout(WAIT_SECONDS * 1000) { real.entries.first { e -> e.all { it.state is JobState.Finished } } } }
            assertFalse(controller.state.busy)
        } finally {
            gate.countDown()
        }
    }

    /** What a user pressing Stop does: ask the running job, by id, to stop. */
    private fun stopAsked(control: JobControl) {
        jobs.stop(jobs.entries.value.last { it.state is JobState.Running }.id)
        assertTrue(control.shouldStop())
    }

    private companion object {
        const val WAIT_SECONDS = 5L
    }
}
