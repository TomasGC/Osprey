package app.osprey.history

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
}
