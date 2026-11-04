package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunRecord
import app.osprey.history.RunRecorder
import app.osprey.history.RunState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-03T10:00:00Z")

class ViewTurnsHistoryTest {

    private val fs = FakeFileSystemRepository()
    private val photo = fs.addFile(fs.root, "a.jpg")
    private val history = InMemoryJobHistoryStore()
    private var tick = 0L
    private val recorder = RunRecorder(history) { T0.plusSeconds(tick++) }

    private fun turnsAnswering(answer: () -> TurnSaveResult) = ViewTurns({ _, _, _ -> answer() }, recorder)

    private fun only(): RunRecord = history.runs().single()

    @Test
    fun `a saved turn is one done run with its file`() {
        val turns = turnsAnswering { TurnSaveResult.Saved }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(
            RunRecord(
                id = 1,
                action = "Save turn",
                state = RunState.Done,
                queuedAt = T0,
                startedAt = T0.plusSeconds(1),
                endedAt = T0.plusSeconds(2),
                summary = "Save turn: a.jpg",
                files = listOf(FileRecord("a.jpg", FileResult.Done, "saved a 90\u00b0 clockwise turn")),
            ),
            only(),
        )
    }

    private val before = FileState(10, T0, T0, "hash-before", "a.jpg")
    private val after = FileState(10, T0, T0, "hash-after", "a.jpg")

    @Test
    fun `a saved turn records the file as it was before and after, read around the save`() {
        val log = mutableListOf<String>()
        val states = mutableListOf(before, after)
        val turns = ViewTurns({ _, _, _ -> log += "save"; TurnSaveResult.Saved }, recorder) { _, _ ->
            log += "state"
            states.removeFirst()
        }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(listOf("state", "save", "state"), log)
        assertEquals(
            FileRecord("a.jpg", FileResult.Done, "saved a 90° clockwise turn", before, after),
            only().files.single(),
        )
    }

    @Test
    fun `a refused save records the file as it was and nothing after`() {
        var lookups = 0
        val turns = ViewTurns({ _, _, _ -> TurnSaveResult.Refused("interlaced PNG") }, recorder) { _, _ ->
            lookups++
            before
        }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(
            listOf(FileRecord("a.jpg", FileResult.Failed, "interlaced PNG", before = before)),
            only().files,
        )
        assertEquals(1, lookups)
    }

    @Test
    fun `a file whose state cannot be read is recorded without it and the save goes on`() {
        val turns = ViewTurns({ _, _, _ -> TurnSaveResult.Saved }, recorder) { _, _ -> null }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(RunState.Done, only().state)
        assertEquals(FileRecord("a.jpg", FileResult.Done, "saved a 90° clockwise turn"), only().files.single())
    }

    @Test
    fun `a state lookup that throws never breaks a save`() {
        val turns = ViewTurns({ _, _, _ -> TurnSaveResult.Saved }, recorder) { _, _ -> error("bug in the lookup") }
        turns.turn(photo, 1)

        val result = turns.save(photo, "a.jpg")

        assertEquals(TurnSaveResult.Saved, result)
        assertEquals(RunState.Done, only().state)
        assertEquals(FileRecord("a.jpg", FileResult.Done, "saved a 90° clockwise turn"), only().files.single())
    }

    @Test
    fun `a turn to the left is recorded in degrees clockwise`() {
        val turns = turnsAnswering { TurnSaveResult.Saved }
        turns.turn(photo, -1)

        turns.save(photo, "a.jpg")

        assertEquals("saved a 270\u00b0 clockwise turn", only().files.single().message)
    }

    @Test
    fun `the note of a good save is kept in the file and in the summary`() {
        val turns = turnsAnswering { TurnSaveResult.SavedWithNote("look here") }
        turns.turn(photo, 1)

        turns.save(photo, "a.heic")

        val run = only()
        assertEquals(RunState.Done, run.state)
        assertEquals("Save turn: a.heic: look here", run.summary)
        assertEquals("saved a 90\u00b0 clockwise turn (look here)", run.files.single().message)
    }

    @Test
    fun `a refused save is a failed run with the reason`() {
        val turns = turnsAnswering { TurnSaveResult.Refused("interlaced PNG") }
        turns.turn(photo, 1)

        turns.save(photo, "a.png")

        val run = only()
        assertEquals(RunState.Failed, run.state)
        assertEquals("Save turn: a.png: interlaced PNG", run.summary)
        assertEquals(listOf(FileRecord("a.png", FileResult.Failed, "interlaced PNG")), run.files)
    }

    @Test
    fun `a failed save is a failed run with the reason`() {
        val turns = turnsAnswering { TurnSaveResult.Failed("disk gone") }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        val run = only()
        assertEquals(RunState.Failed, run.state)
        assertEquals("Save turn: a.jpg: Could not save the turn: disk gone", run.summary)
        assertEquals(listOf(FileRecord("a.jpg", FileResult.Failed, "disk gone")), run.files)
    }

    @Test
    fun `a saver that throws is a failed run with its message`() {
        val turns = turnsAnswering { error("boom") }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(RunState.Failed, only().state)
        assertEquals("boom", only().files.single().message)
    }

    @Test
    fun `a second save of a photo already being saved records no run of its own`() {
        lateinit var turns: ViewTurns
        turns = turnsAnswering {
            turns.save(photo, "a.jpg")
            TurnSaveResult.Saved
        }
        turns.turn(photo, 1)

        turns.save(photo, "a.jpg")

        assertEquals(1, history.runs().size)
    }

    @Test
    fun `a photo with no pending turn records nothing`() {
        val turns = turnsAnswering { TurnSaveResult.Saved }

        turns.save(photo, "a.jpg")

        assertEquals(emptyList<RunRecord>(), history.runs())
    }
}
