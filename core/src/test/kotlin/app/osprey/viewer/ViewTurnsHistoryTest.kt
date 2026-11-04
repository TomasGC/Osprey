package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
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
