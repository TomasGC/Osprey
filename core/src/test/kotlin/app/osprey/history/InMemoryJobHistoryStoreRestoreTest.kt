package app.osprey.history

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryJobHistoryStoreRestoreTest {

    private val t0: Instant = Instant.parse("2026-11-03T10:00:00Z")

    private fun run(id: Long, state: RunState = RunState.Done) = RunRecord(id, "R$id", state, queuedAt = t0)

    @Test
    fun `restored runs are listed newest first and ids go on after the highest`() {
        val store = InMemoryJobHistoryStore(listOf(run(4), run(2)))

        assertEquals(listOf(4L, 2L), store.runs().map { it.id })
        assertEquals(5L, store.begin("New", t0))
    }

    @Test
    fun `a restored run can be written to`() {
        val store = InMemoryJobHistoryStore(listOf(run(4, RunState.Running)))

        store.appendLog(4, "more")

        assertEquals(listOf("more"), store.runs().single().log)
    }

    @Test
    fun `restored runs that ended over a day ago are dropped, the newest three stay, ids go on after the highest`() {
        val now = t0.plus(Duration.ofDays(3))
        val runs = (1L..5L).map { RunRecord(it, "R$it", RunState.Done, queuedAt = t0, endedAt = t0) }

        val store = InMemoryJobHistoryStore(runs, now)

        assertEquals(listOf(5L, 4L, 3L), store.runs().map { it.id })
        assertEquals(6L, store.begin("New", now))
    }
}
