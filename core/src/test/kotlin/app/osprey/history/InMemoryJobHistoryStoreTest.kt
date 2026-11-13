package app.osprey.history

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-02T10:00:00Z")
private val T1: Instant = T0.plusSeconds(60)
private val NOW: Instant = Instant.parse("2026-11-05T10:00:00Z")

private fun endedRun(id: Long, endedAt: Instant?, state: RunState = RunState.Done) =
    RunRecord(id, "R$id", state, queuedAt = T0, endedAt = endedAt)

private fun runWithFiles(id: Long, files: Int, state: RunState = RunState.Done) = RunRecord(
    id, "R$id", state, queuedAt = T0, endedAt = if (state.isActive) null else NOW,
    files = List(files) { FileRecord("f$it", FileResult.Done) },
)

class InMemoryJobHistoryStoreTest {

    private val store = InMemoryJobHistoryStore()

    private fun ended(action: String): Long =
        store.begin(action, T0).also { store.finish(it, RunState.Done, "$action done", T1) }

    @Test
    fun `a new run is queued and listed with its action`() {
        val id = store.begin("Reorg", T0)

        assertEquals(RunRecord(id, "Reorg", RunState.Queued, queuedAt = T0), store.runs().single())
    }

    @Test
    fun `ids grow and runs are listed newest first`() {
        val first = store.begin("A", T0)
        val second = store.begin("B", T0)

        assertTrue(second > first)
        assertEquals(listOf(second, first), store.runs().map { it.id })
    }

    @Test
    fun `a started run is running with its start time`() {
        val id = store.begin("Reorg", T0)

        store.start(id, T1)

        val run = store.runs().single()
        assertEquals(RunState.Running, run.state)
        assertEquals(T1, run.startedAt)
    }

    @Test
    fun `log lines and file records are kept in the order they came`() {
        val id = store.begin("Dates", T0)
        val a = FileRecord("a.jpg", FileResult.Done, before = FileState(1), after = FileState(1, modified = T1))
        val b = FileRecord("b.jpg", FileResult.Failed, "locked")

        store.appendLog(id, "first")
        store.appendFile(id, a)
        store.appendLog(id, "second")
        store.appendFile(id, b)

        val run = store.runs().single()
        assertEquals(listOf("first", "second"), run.log)
        assertEquals(listOf(a, b), run.files)
    }

    @Test
    fun `a finished run keeps its state, summary and end time`() {
        val id = store.begin("Rename", T0)

        store.finish(id, RunState.Failed, "Rename failed: boom", T1)

        val run = store.runs().single()
        assertEquals(RunState.Failed, run.state)
        assertEquals("Rename failed: boom", run.summary)
        assertEquals(T1, run.endedAt)
    }

    @Test
    fun `a call about an unknown run changes nothing`() {
        val id = store.begin("Reorg", T0)

        store.start(id + 1, T1)
        store.appendLog(id + 1, "x")
        store.appendFile(id + 1, FileRecord("a", FileResult.Done))
        store.finish(id + 1, RunState.Done, "x", T1)

        assertEquals(RunRecord(id, "Reorg", RunState.Queued, queuedAt = T0), store.runs().single())
    }

    @Test
    fun `no run is dropped while the app runs`() {
        val ids = (1..12).map { ended("Run $it") }

        assertEquals(ids.reversed(), store.runs().map { it.id })
    }

    @Test
    fun `clear forgets ended runs and keeps active ones`() {
        ended("Old")
        val waiting = store.begin("Waiting", T0)

        store.clear()

        assertEquals(listOf(waiting), store.runs().map { it.id })
    }

    @Test
    fun `runs left active at startup become interrupted`() {
        val queued = store.begin("Queued", T0)
        val running = store.begin("Running", T0).also { store.start(it, T0) }
        val done = ended("Done")

        store.closeInterrupted(T1)

        val byId = store.runs().associateBy { it.id }
        assertEquals(RunState.Interrupted, byId.getValue(queued).state)
        assertEquals(RunState.Interrupted, byId.getValue(running).state)
        assertEquals(T1, byId.getValue(running).endedAt)
        assertEquals(RunState.Done, byId.getValue(done).state)
        assertNull(byId.getValue(done).startedAt)
    }

    @Test
    fun `only a queued or running state is active`() {
        assertEquals(
            listOf(RunState.Queued, RunState.Running),
            RunState.entries.filter { it.isActive },
        )
    }

    @Test
    fun `nothing is dropped while every run ended within a day`() {
        val runs = (1L..5L).map { endedRun(it, NOW.minus(Duration.ofHours(1))) }

        assertEquals(emptyList<Long>(), runsToDrop(runs, NOW))
    }

    @Test
    fun `a run is dropped once a day has passed since it ended, not before`() {
        val runs = listOf(
            endedRun(1, NOW.minus(Duration.ofHours(24))),
            endedRun(2, NOW.minus(Duration.ofHours(24)).plusMillis(1)),
            endedRun(3, NOW),
            endedRun(4, NOW),
            endedRun(5, NOW),
        )

        assertEquals(listOf(1L), runsToDrop(runs, NOW))
    }

    @Test
    fun `the three newest ended runs stay whatever their age`() {
        val old = NOW.minus(Duration.ofDays(10))
        val runs = (5L downTo 1L).map { endedRun(it, old) }

        assertEquals(listOf(1L, 2L), runsToDrop(runs, NOW).sorted())
    }

    @Test
    fun `fewer than three ended runs are all kept`() {
        val runs = listOf(endedRun(1, NOW.minus(Duration.ofDays(9))), endedRun(2, NOW.minus(Duration.ofDays(9))))

        assertEquals(emptyList<Long>(), runsToDrop(runs, NOW))
    }

    @Test
    fun `a queued or running run is never dropped and is not counted among the newest three`() {
        val old = NOW.minus(Duration.ofDays(10))
        val runs = listOf(
            RunRecord(1, "Running", RunState.Running, queuedAt = old),
            RunRecord(2, "Queued", RunState.Queued, queuedAt = old),
            endedRun(3, old),
            endedRun(4, old),
            endedRun(5, old),
            endedRun(6, old),
        )

        assertEquals(listOf(3L), runsToDrop(runs, NOW))
    }

    @Test
    fun `an ended run with no end time counts from when it was queued`() {
        val runs = listOf(endedRun(1, null), endedRun(2, NOW), endedRun(3, NOW), endedRun(4, NOW))

        assertEquals(listOf(1L), runsToDrop(runs, NOW))
    }

    @Test
    fun `nothing is dropped while the file records fit the cap`() {
        val runs = (1L..5L).map { runWithFiles(it, 10) }

        assertEquals(emptyList<Long>(), runsOverFileCap(runs, maxRecords = 50))
    }

    @Test
    fun `the oldest ended runs are dropped until the file records fit the cap`() {
        val runs = listOf(runWithFiles(1, 10), runWithFiles(2, 10)) + (3L..5L).map { runWithFiles(it, 5) }

        assertEquals(listOf(1L), runsOverFileCap(runs, maxRecords = 25))
    }

    @Test
    fun `the three newest ended runs stay even when they alone exceed the cap`() {
        val runs = (1L..4L).map { runWithFiles(it, 10) }

        assertEquals(listOf(1L), runsOverFileCap(runs, maxRecords = 5))
    }

    @Test
    fun `a running run is not counted against the cap and never dropped`() {
        val runs = listOf(runWithFiles(1, 100, RunState.Running)) + (2L..5L).map { runWithFiles(it, 10) }

        assertEquals(emptyList<Long>(), runsOverFileCap(runs, maxRecords = 45))
        assertEquals(listOf(2L), runsOverFileCap(runs, maxRecords = 35))
    }

    @Test
    fun `at the start the age rule applies first and the cap counts what is left`() {
        val old = RunRecord(1, "R1", RunState.Done, queuedAt = T0, endedAt = NOW.minus(Duration.ofDays(2)))
        val runs = listOf(old) + (2L..5L).map { runWithFiles(it, 10) }

        assertEquals(listOf(1L, 2L), runsToDrop(runs, NOW, maxRecords = 25))
    }

    @Test
    fun `a run that ends over the file cap drops the oldest ended run, and no age rule applies`() {
        val capped = InMemoryJobHistoryStore(maxFileRecords = 25)
        val ids = (1..4).map { n ->
            val id = capped.begin("R$n", T0)
            repeat(10) { capped.appendFile(id, FileRecord("f$it", FileResult.Done)) }
            capped.finish(id, RunState.Done, "ok", T1)
            id
        }

        assertEquals(ids.drop(1).reversed(), capped.runs().map { it.id })
    }

    @Test
    fun `every write raises the revision`() {
        assertEquals(0L, store.revision.value)
        val id = store.begin("A", T0)
        val writes: List<() -> Unit> = listOf(
            { store.start(id, T0) },
            { store.appendLog(id, "x") },
            { store.appendFile(id, FileRecord("a", FileResult.Done)) },
            { store.finish(id, RunState.Done, "s", T1) },
            { store.closeInterrupted(T1) },
            { store.clear() },
            { store.begin("B", T0) },
        )

        for (write in writes) {
            val before = store.revision.value
            write()
            assertTrue(store.revision.value > before)
        }
    }
}
