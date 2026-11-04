package app.osprey.history

import java.io.File
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
    fun `a folder that does not exist gives an empty history and writes nothing`() {
        val history = openHistory(dir)

        assertTrue(history.runs().isEmpty())
        assertFalse(dir.exists())
    }
}
