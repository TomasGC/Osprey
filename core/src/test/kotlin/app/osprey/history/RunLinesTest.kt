package app.osprey.history

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun at(ms: Long): Instant = Instant.ofEpochMilli(ms)

private val FULL_FILE = FileRecord(
    "/a/b.jpg", FileResult.Done, "",
    before = FileState(5, created = at(10), modified = at(20), hash = "h", name = "b.jpg"),
    after = FileState(6),
)

class RunLinesTest {

    private val everyKind = listOf(
        RunLine.Header(3, "Reorg", at(1000)) to """{"t":"run","id":3,"action":"Reorg","at":1000}""",
        RunLine.Started(at(2000)) to """{"t":"start","at":2000}""",
        RunLine.Logged("a \"b\"") to """{"t":"log","line":"a \"b\""}""",
        RunLine.Filed(FileRecord("/a/b.jpg", FileResult.Failed, "boom")) to
            """{"t":"file","path":"/a/b.jpg","result":"Failed","message":"boom"}""",
        RunLine.Filed(FULL_FILE) to
            """{"t":"file","path":"/a/b.jpg","result":"Done","message":"",""" +
            """"before":{"size":5,"created":10,"modified":20,"hash":"h","name":"b.jpg"},"after":{"size":6}}""",
        RunLine.Ended(RunState.Done, "ok", at(3000)) to """{"t":"end","state":"Done","summary":"ok","at":3000}""",
    )

    @Test
    fun `every kind of line has its exact text`() {
        everyKind.forEach { (line, text) -> assertEquals(text, RunLines.encode(line)) }
    }

    @Test
    fun `every kind of line reads back as it was written`() {
        everyKind.forEach { (line, text) -> assertEquals(line, RunLines.decode(text)) }
    }

    @Test
    fun `a time keeps its milliseconds and loses what is finer`() {
        val line = RunLine.Started(Instant.parse("2026-11-03T10:00:00.123456789Z"))

        assertEquals(RunLine.Started(Instant.parse("2026-11-03T10:00:00.123Z")), RunLines.decode(RunLines.encode(line)))
    }

    @Test
    fun `every state and result name survives`() {
        RunState.entries.forEach {
            val line = RunLine.Ended(it, "", at(1))
            assertEquals(line, RunLines.decode(RunLines.encode(line)))
        }
        FileResult.entries.forEach {
            val line = RunLine.Filed(FileRecord("p", it))
            assertEquals(line, RunLines.decode(RunLines.encode(line)))
        }
    }

    @Test
    fun `a line that cannot be read is null`() {
        listOf(
            "", "not json", """{"t":"log","li""", "[1]", "42", "{}", """{"t":"nope"}""", """{"t":5}""",
            """{"t":"run","action":"A","at":1}""", """{"t":"run","id":"3","action":"A","at":1}""",
            """{"t":"run","id":3,"at":1}""", """{"t":"run","id":3,"action":"A"}""",
            """{"t":"run","id":3,"action":"A","at":"x"}""",
            """{"t":"start"}""", """{"t":"log"}""", """{"t":"log","line":3}""",
            """{"t":"file","result":"Done"}""", """{"t":"file","path":"p"}""",
            """{"t":"file","path":"p","result":"Nope"}""", """{"t":"file","path":"p","result":3}""",
            """{"t":"file","path":"p","result":"done"}""",
            """{"t":"end","summary":"s","at":1}""", """{"t":"end","state":"Nope","summary":"s","at":1}""",
            """{"t":"end","state":"done","summary":"s","at":1}""",
            """{"t":"end","state":"Done","summary":"s"}""",
        ).forEach { assertNull(it, RunLines.decode(it)) }
    }

    @Test
    fun `what is optional may be missing or wrong without losing the line`() {
        assertEquals(
            RunLine.Filed(FileRecord("p", FileResult.Done)),
            RunLines.decode("""{"t":"file","path":"p","result":"Done"}"""),
        )
        assertEquals(
            RunLine.Filed(FileRecord("p", FileResult.Done, "", before = null, after = FileState(1))),
            RunLines.decode("""{"t":"file","path":"p","result":"Done","message":7,"before":3,"after":{"size":1,"created":"x"}}"""),
        )
        assertEquals(
            RunLine.Filed(FileRecord("p", FileResult.Done)),
            RunLines.decode("""{"t":"file","path":"p","result":"Done","before":{"created":1},"after":{}}"""),
        )
        assertEquals(
            RunLine.Ended(RunState.Interrupted, "", at(9)),
            RunLines.decode("""{"t":"end","state":"Interrupted","at":9}"""),
        )
    }

    @Test
    fun `a field a later version adds is ignored`() {
        assertEquals(
            RunLine.Logged("x"),
            RunLines.decode("""{"t":"log","line":"x","level":"info"}"""),
        )
    }

    private fun replay(vararg lines: RunLine) = RunLines.replay(lines.map { RunLines.encode(it) })

    @Test
    fun `a whole run is rebuilt from its lines`() {
        val gone = FileRecord("/a/c.jpg", FileResult.Skipped, "no longer there")
        val run = replay(
            RunLine.Header(3, "Reorg", at(1000)),
            RunLine.Started(at(2000)),
            RunLine.Logged("one"),
            RunLine.Filed(FULL_FILE),
            RunLine.Logged("two"),
            RunLine.Filed(gone),
            RunLine.Ended(RunState.Failed, "1 failed", at(3000)),
        )

        assertEquals(
            RunRecord(
                3, "Reorg", RunState.Failed, queuedAt = at(1000), startedAt = at(2000), endedAt = at(3000),
                summary = "1 failed", log = listOf("one", "two"), files = listOf(FULL_FILE, gone),
            ),
            run,
        )
    }

    @Test
    fun `a run cut short is still what it had reached`() {
        assertEquals(RunState.Queued, replay(RunLine.Header(1, "A", at(1)))!!.state)

        val running = replay(RunLine.Header(1, "A", at(1)), RunLine.Started(at(2)), RunLine.Logged("x"))!!

        assertEquals(RunState.Running, running.state)
        assertEquals(at(2), running.startedAt)
        assertNull(running.endedAt)
        assertEquals(listOf("x"), running.log)
    }

    @Test
    fun `an interrupted run keeps what it had written`() {
        val run = replay(
            RunLine.Header(1, "A", at(1)), RunLine.Started(at(2)), RunLine.Logged("x"),
            RunLine.Ended(RunState.Interrupted, "", at(9)),
        )!!

        assertEquals(RunState.Interrupted, run.state)
        assertEquals(at(9), run.endedAt)
        assertEquals(listOf("x"), run.log)
    }

    @Test
    fun `lines that cannot be read are skipped, a cut last line included`() {
        val lines = listOf(
            RunLines.encode(RunLine.Header(1, "A", at(1))),
            "garbage",
            RunLines.encode(RunLine.Logged("kept")),
            """{"t":"log","line":"cut ha""",
        )

        val run = RunLines.replay(lines)!!

        assertEquals(listOf("kept"), run.log)
        assertEquals(RunState.Queued, run.state)
    }

    @Test
    fun `lines before the header and a second header are ignored`() {
        val run = replay(
            RunLine.Logged("early"),
            RunLine.Header(1, "A", at(1)),
            RunLine.Header(2, "B", at(2)),
            RunLine.Logged("late"),
        )!!

        assertEquals(1L, run.id)
        assertEquals("A", run.action)
        assertEquals(listOf("late"), run.log)
    }

    @Test
    fun `a file with no readable header is no run`() {
        assertNull(RunLines.replay(emptyList()))
        assertNull(RunLines.replay(listOf("garbage", RunLines.encode(RunLine.Logged("x")))))
    }
}
