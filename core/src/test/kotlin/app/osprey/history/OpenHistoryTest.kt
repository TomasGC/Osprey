package app.osprey.history

import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenHistoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "history") }

    @Test
    fun `a run an earlier launch left unfinished is closed as interrupted`() {
        val earlier = FileJobHistoryStore(dir)
        val running = earlier.begin("Reorg", Instant.ofEpochMilli(1))
        earlier.start(running, Instant.ofEpochMilli(2))
        val done = earlier.begin("Dates", Instant.ofEpochMilli(3))
        earlier.finish(done, RunState.Done, "ok", Instant.ofEpochMilli(4))

        val history = openHistory(dir, Instant.ofEpochMilli(9))

        val byId = history.runs().associateBy { it.id }
        assertEquals(RunState.Interrupted, byId.getValue(running).state)
        assertEquals(Instant.ofEpochMilli(9), byId.getValue(running).endedAt)
        assertEquals(RunState.Done, byId.getValue(done).state)
        assertEquals(history.runs(), FileJobHistoryStore(dir).runs())
    }

    @Test
    fun `runs that ended a day before the launch are removed, the newest three stay`() {
        val earlier = FileJobHistoryStore(dir, Instant.ofEpochMilli(0))
        repeat(5) {
            val id = earlier.begin("R$it", Instant.ofEpochMilli(1))
            earlier.finish(id, RunState.Done, "ok", Instant.ofEpochMilli(2))
        }

        val history = openHistory(dir, Instant.ofEpochMilli(0).plus(Duration.ofHours(25)))

        assertEquals(listOf(5L, 4L, 3L), history.runs().map { it.id })
        assertEquals(listOf("3.jsonl", "4.jsonl", "5.jsonl"), dir.list().orEmpty().sorted())
    }

    @Test
    fun `a folder that does not exist gives an empty history and writes nothing`() {
        val history = openHistory(dir)

        assertTrue(history.runs().isEmpty())
        assertFalse(dir.exists())
    }
}
