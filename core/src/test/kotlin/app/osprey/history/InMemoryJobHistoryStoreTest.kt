package app.osprey.history

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-02T10:00:00Z")
private val T1: Instant = T0.plusSeconds(60)

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
    fun `only the newest ten runs are kept`() {
        val ids = (1..12).map { ended("Run $it") }

        assertEquals(ids.takeLast(MAX_RUNS).reversed(), store.runs().map { it.id })
    }

    @Test
    fun `a running run is never dropped to make room`() {
        val running = store.begin("Long", T0).also { store.start(it, T0) }
        repeat(11) { ended("Run $it") }

        val kept = store.runs()
        assertEquals(MAX_RUNS, kept.size)
        assertTrue(kept.any { it.id == running && it.state == RunState.Running })
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
    fun `nothing is dropped while the history fits`() {
        val runs = (1L..3L).map { RunRecord(it, "A", RunState.Done, T0) }

        assertEquals(emptyList<Long>(), runsToDrop(runs, max = 3))
    }

    @Test
    fun `the oldest ended runs are dropped first and active ones never`() {
        val runs = listOf(
            RunRecord(1, "A", RunState.Running, T0),
            RunRecord(2, "B", RunState.Done, T0),
            RunRecord(3, "C", RunState.Failed, T0),
            RunRecord(4, "D", RunState.Done, T0),
        )

        assertEquals(listOf(2L, 3L), runsToDrop(runs, max = 2))
    }
}
