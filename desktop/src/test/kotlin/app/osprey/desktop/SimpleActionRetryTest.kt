package app.osprey.desktop

import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.PlanFilter
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.jobs.JobControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LIBRARY = "D:/Photos"
private val ALL = listOf("a.jpg", "b.jpg", "c.jpg")
private val BY_NAME = PlanFilter<List<String>>(names = { it }, only = { plan, names -> plan.filter { it in names } })

/** The retry of the failed files of a run (#83) on the generic scan-then-apply controller. */
class SimpleActionRetryTest : TimedTest() {

    private val history = InMemoryJobHistoryStore()
    private val jobs = immediateJobQueue(history)
    private val applied = mutableListOf<List<String>>()
    private var scanned = ALL
    private var failing = setOf("b.jpg")
    private var library = LIBRARY

    private fun controller(retry: RetryWiring<List<String>>? = RetryWiring("dedupe", { library }, BY_NAME)) =
        SimpleActionController(
            name = "Test",
            jobs = jobs,
            scan = { _, _ -> ScanResult(scanned, "count=${scanned.size}", emptyList(), scanned.size) },
            apply = { _, plan, control -> applyPlan(plan, control) },
            retry = retry,
        )

    private fun applyPlan(plan: List<String>, control: JobControl): Int {
        applied += plan
        plan.forEach {
            val result = if (it in failing) FileResult.Failed else FileResult.Done
            control.record(FileRecord(it, result, if (result == FileResult.Failed) "locked" else "ok"))
        }
        return plan.size - plan.count { it in failing }
    }

    private fun failedRun(controller: SimpleActionController<List<String>>, scope: String? = "2021"): RunRecord {
        controller.onScan(FakeHandle, scope)
        controller.onApply(FakeHandle, scope)
        return history.runs().first { it.action == "Test 2021 apply" }
    }

    private fun retries() = history.runs().filter { it.action == "Test 2021 retry" }

    @Test
    fun `an apply run is recorded with where it came from and the scan is not`() {
        val run = failedRun(controller())

        assertEquals(RunOrigin("dedupe", LIBRARY, "2021"), run.origin)
        assertNull(history.runs().first { it.action == "Test 2021 scan" }.origin)
    }

    @Test
    fun `the run that failed on a file is a failed run with that file recorded under its scope`() {
        val run = failedRun(controller())

        assertEquals(RunState.Failed, run.state)
        assertEquals("2021/b.jpg", run.files.single { it.result == FileResult.Failed }.path)
    }

    @Test
    fun `a retry applies only the files the run failed on`() {
        val controller = controller()
        val run = failedRun(controller)
        failing = emptySet()

        controller.onRetry(FakeHandle, run, "2021")

        assertEquals(listOf(ALL, listOf("b.jpg")), applied)
        assertFalse(controller.state.busy)
    }

    @Test
    fun `a retry is its own run, linked to the first, and the first is left as it was`() {
        val controller = controller()
        val run = failedRun(controller)
        failing = emptySet()

        controller.onRetry(FakeHandle, run, "2021")

        val retry = retries().single()
        assertEquals(RunOrigin("dedupe", LIBRARY, "2021", retryOf = run.id), retry.origin)
        assertEquals("Retry of run #${run.id}: 1 done", retry.summary)
        assertEquals(RunState.Done, retry.state)
        assertEquals(run, history.runs().first { it.id == run.id })
    }

    @Test
    fun `a file that still fails is failed again in the retry with its new reason`() {
        val controller = controller()
        val run = failedRun(controller)

        controller.onRetry(FakeHandle, run, "2021")

        val retry = retries().single()
        assertEquals(RunState.Failed, retry.state)
        assertEquals("Retry of run #${run.id}: 0 done, 1 failed", retry.summary)
        assertEquals(listOf("2021/b.jpg"), retry.files.filter { it.result == FileResult.Failed }.map { it.path })
    }

    @Test
    fun `a failed file the new scan no longer finds is reported skipped, not fatal`() {
        val controller = controller()
        val run = failedRun(controller)
        scanned = listOf("a.jpg", "c.jpg")

        controller.onRetry(FakeHandle, run, "2021")

        val retry = retries().single()
        assertEquals(
            listOf(FileRecord("2021/b.jpg", FileResult.Skipped, "no longer there or already handled")),
            retry.files,
        )
        assertEquals(listOf(ALL), applied)
    }

    @Test
    fun `a run that cannot be retried starts nothing`() {
        val controller = controller()
        val done = failedRun(controller).copy(state = RunState.Done)

        controller.onRetry(FakeHandle, done, "2021")

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a run of another library or scope starts nothing`() {
        val controller = controller()
        val run = failedRun(controller)

        library = "E:/Other"
        controller.onRetry(FakeHandle, run, "2021")
        library = LIBRARY
        controller.onRetry(FakeHandle, run, "2022")

        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a controller with no retry wiring records no origin and cannot retry`() {
        val bare = controller(retry = null)
        val run = failedRun(bare)

        bare.onRetry(FakeHandle, run, "2021")

        assertNull(run.origin)
        assertTrue(retries().isEmpty())
    }

    @Test
    fun `a retry does nothing while the controller is busy`() {
        val controller = controller()
        val run = failedRun(controller)
        controller.state.busy = true

        controller.onRetry(FakeHandle, run, "2021")

        assertTrue(retries().isEmpty())
    }
}
