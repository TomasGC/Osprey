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

private fun at(ms: Long): Instant = Instant.ofEpochMilli(ms)

class FileJobHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "history") }

    private fun store() = FileJobHistoryStore(dir, at(10))

    private fun file(id: Long) = File(dir, "$id.jsonl")

    private fun names() = dir.list().orEmpty().sorted()

    private fun ended(store: JobHistoryStore, action: String): Long =
        store.begin(action, at(1)).also { store.finish(it, RunState.Done, "$action done", at(2)) }

    private fun fullRun(store: JobHistoryStore): Long {
        val id = store.begin("Reorg", at(1000))
        store.start(id, at(2000))
        store.appendLog(id, "one")
        store.appendFile(id, FileRecord("/a.jpg", FileResult.Done, "moved"))
        store.finish(id, RunState.Failed, "1 failed", at(3000))
        return id
    }

    @Test
    fun `a run is written to its own file as it happens`() {
        val store = store()

        val id = store.begin("Reorg", at(1000))
        assertEquals(listOf(RunLines.encode(RunLine.Header(id, "Reorg", at(1000)))), file(id).readLines())
        store.start(id, at(2000))
        store.appendLog(id, "one")
        store.appendFile(id, FileRecord("/a.jpg", FileResult.Done, "moved"))
        assertEquals(4, file(id).readLines().size)
        store.finish(id, RunState.Failed, "1 failed", at(3000))

        assertEquals(store.runs().single(), RunLines.replay(file(id).readLines()))
        assertEquals(listOf("1.jsonl"), names())
    }

    @Test
    fun `a new store on the same folder shows the same runs`() {
        val first = store()
        fullRun(first)
        ended(first, "Dates")

        assertEquals(first.runs(), store().runs())
    }

    @Test
    fun `ids go on after the highest one kept`() {
        val first = store()
        ended(first, "A")
        ended(first, "B")

        assertEquals(3L, store().begin("C", at(5)))
    }

    @Test
    fun `ids are not reused after Clear history while the app keeps running`() {
        val store = store()
        ended(store, "A")
        ended(store, "B")
        store.clear()

        assertEquals(3L, store.begin("C", at(5)))
    }

    @Test
    fun `ids start again at one after Clear history and a restart, since no file is left to count from`() {
        val first = store()
        ended(first, "A")
        ended(first, "B")
        first.clear()

        assertEquals(1L, store().begin("C", at(5)))
    }

    @Test
    fun `a line cut by a crash is ignored and what came before is kept`() {
        val first = store()
        val id = first.begin("A", at(1))
        first.start(id, at(2))
        first.appendLog(id, "kept")
        file(id).appendText("""{"t":"log","li""")

        val run = store().runs().single()

        assertEquals(listOf("kept"), run.log)
        assertEquals(RunState.Running, run.state)
    }

    @Test
    fun `a line written after a cut one is not glued to it`() {
        val first = store()
        val id = first.begin("A", at(1))
        first.start(id, at(2))
        file(id).appendText("""{"t":"log","li""")

        val second = store()
        second.closeInterrupted(at(9))

        assertEquals(RunState.Interrupted, store().runs().single().state)
        assertEquals(at(9), store().runs().single().endedAt)
    }

    @Test
    fun `a run still active at start is marked interrupted, in memory and in its file`() {
        val first = store()
        val waiting = first.begin("Waiting", at(1))
        val running = first.begin("Running", at(1))
        first.start(running, at(2))
        val done = ended(first, "Done")

        val second = store()
        second.closeInterrupted(at(9))

        for (runs in listOf(second.runs(), store().runs())) {
            val byId = runs.associateBy { it.id }
            assertEquals(RunState.Interrupted, byId.getValue(waiting).state)
            assertEquals(RunState.Interrupted, byId.getValue(running).state)
            assertEquals(at(9), byId.getValue(running).endedAt)
            assertEquals(RunState.Done, byId.getValue(done).state)
        }
    }

    @Test
    fun `no file goes while the app runs`() {
        val store = store()

        repeat(12) { ended(store, "R$it") }

        assertEquals(12, store.runs().size)
        assertEquals(12, names().size)
    }

    @Test
    fun `clear deletes the files of ended runs and keeps an active one`() {
        val store = store()
        ended(store, "Old")
        val active = store.begin("Waiting", at(3))

        store.clear()

        assertEquals(listOf("$active.jsonl"), names())
        assertEquals(listOf(active), store.runs().map { it.id })
        assertEquals(store.runs(), store().runs())
    }

    @Test
    fun `a file that cannot be read is skipped and left alone`() {
        dir.mkdirs()
        File(dir, "junk.jsonl").writeText("garbage\nmore\n")
        File(dir, "empty.jsonl").writeText("")
        File(dir, "folder.jsonl").mkdirs()
        File(dir, "notes.txt").writeText("x")
        val store = store()

        ended(store, "A")
        assertEquals(1, store.runs().size)
        store.clear()

        assertEquals(listOf("empty.jsonl", "folder.jsonl", "junk.jsonl", "notes.txt"), names())
    }

    @Test
    fun `runs that ended over a day before the start are removed with their files, the newest three stay`() {
        dir.mkdirs()
        (1L..6L).forEach {
            file(it).writeText(
                RunLines.encode(RunLine.Header(it, "R$it", at(it))) + "\n" +
                    RunLines.encode(RunLine.Ended(RunState.Done, "ok", at(it + 1))) + "\n",
            )
        }

        val store = FileJobHistoryStore(dir, at(0).plus(Duration.ofHours(25)))

        assertEquals(listOf(6L, 5L, 4L), store.runs().map { it.id })
        assertFalse(file(1).exists() || file(2).exists() || file(3).exists())
        assertTrue(file(4).exists())
        assertEquals(7L, store.begin("New", at(99)))
    }

    @Test
    fun `at the start the cap on file records drops the oldest ended runs and their files`() {
        dir.mkdirs()
        (1L..5L).forEach { id ->
            val filed = (1..10).joinToString("") {
                RunLines.encode(RunLine.Filed(FileRecord("f$it", FileResult.Done))) + "\n"
            }
            file(id).writeText(
                RunLines.encode(RunLine.Header(id, "R$id", at(id))) + "\n" + filed +
                    RunLines.encode(RunLine.Ended(RunState.Done, "ok", at(id + 1))) + "\n",
            )
        }

        val store = FileJobHistoryStore(dir, at(10), maxFileRecords = 25)

        assertEquals(listOf(5L, 4L, 3L), store.runs().map { it.id })
        assertEquals(listOf("3.jsonl", "4.jsonl", "5.jsonl"), names())
    }

    @Test
    fun `a run that ends over the cap goes with the oldest file`() {
        val store = FileJobHistoryStore(dir, at(10), maxFileRecords = 25)

        repeat(4) {
            val id = store.begin("R$it", at(1))
            repeat(10) { n -> store.appendFile(id, FileRecord("f$n", FileResult.Done)) }
            store.finish(id, RunState.Done, "ok", at(2))
        }

        assertEquals(listOf(4L, 3L, 2L), store.runs().map { it.id })
        assertFalse(file(1).exists())
        assertEquals(3, names().size)
    }

    @Test
    fun `two files with the same run id show one run`() {
        dir.mkdirs()
        listOf("a.jsonl", "b.jsonl").forEach {
            File(dir, it).writeText(RunLines.encode(RunLine.Header(5, "R5", at(1))) + "\n")
        }

        assertEquals(listOf(5L), store().runs().map { it.id })
    }

    @Test
    fun `a folder that cannot be written does not break the store`() {
        val blocked = tmp.newFile("blocked")
        val store = FileJobHistoryStore(blocked)

        val id = ended(store, "A")

        assertEquals(RunState.Done, store.runs().single().state)
        assertEquals(id, store.runs().single().id)
        assertTrue(blocked.isFile)
    }

    @Test
    fun `a write that fails loses its line, not the run`() {
        dir.mkdirs()
        file(1).mkdirs()
        val store = store()

        val id = store.begin("A", at(1))
        store.appendLog(id, "x")

        assertEquals(1L, id)
        assertEquals(listOf("x"), store.runs().single().log)
    }

    @Test
    fun `a call about an unknown run writes nothing`() {
        val store = store()

        store.start(99, at(1))
        store.appendLog(99, "x")
        store.appendFile(99, FileRecord("p", FileResult.Done))
        store.finish(99, RunState.Done, "x", at(1))

        assertTrue(names().isEmpty())
        assertTrue(store.runs().isEmpty())
        assertFalse(dir.exists())
    }

    @Test
    fun `a file that is not a run file is not read`() {
        dir.mkdirs()
        File(dir, "old.txt").writeText(RunLines.encode(RunLine.Header(7, "Old", at(1))) + "\n")

        assertTrue(store().runs().isEmpty())
    }

    @Test
    fun `a cut line is closed once, not before every later line`() {
        val first = store()
        val id = first.begin("A", at(1))
        first.start(id, at(2))
        file(id).appendText("""{"t":"log","li""")
        val second = store()

        second.appendLog(id, "a")
        second.appendLog(id, "b")

        val lines = file(id).readLines()
        assertEquals(5, lines.size)
        assertTrue(lines.none { it.isEmpty() })
    }

    @Test
    fun `interrupted runs keep their files, since nothing is dropped after the start`() {
        dir.mkdirs()
        (1L..11L).forEach { file(it).writeText(RunLines.encode(RunLine.Header(it, "R$it", at(it))) + "\n") }
        val store = store()
        assertEquals(11, store.runs().size)

        store.closeInterrupted(at(50))

        assertEquals(11, store.runs().size)
        assertTrue(file(1).exists())
    }

    @Test
    fun `the revision rises with every write`() {
        val store = store()
        val before = store.revision.value

        val id = store.begin("A", at(1))
        val afterBegin = store.revision.value
        store.appendLog(id, "x")

        assertTrue(afterBegin > before)
        assertTrue(store.revision.value > afterBegin)
    }
}
