package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTurnsTest {

    private val fs = FakeFileSystemRepository()
    private val photo = fs.addFile(fs.root, "a.jpg")
    private val other = fs.addFile(fs.root, "b.jpg")
    private val turns = ViewTurns()

    @Test
    fun `a photo nobody turned has no turn`() {
        assertEquals(0, turns.turnsOf(photo))
    }

    @Test
    fun `turning right adds a quarter turn and turning left takes one away`() {
        turns.turn(photo, 1)
        assertEquals(1, turns.turnsOf(photo))

        turns.turn(photo, 1)
        turns.turn(photo, -1)
        assertEquals(1, turns.turnsOf(photo))
    }

    @Test
    fun `turning left from upright goes to three quarter turns`() {
        turns.turn(photo, -1)

        assertEquals(3, turns.turnsOf(photo))
    }

    @Test
    fun `four turns in a row are no turn and leave no entry`() {
        repeat(4) { turns.turn(photo, 1) }

        assertEquals(0, turns.turnsOf(photo))
        assertTrue(turns.turns.value.isEmpty())
    }

    @Test
    fun `each photo keeps its own turn`() {
        turns.turn(photo, 1)

        assertEquals(1, turns.turnsOf(photo))
        assertEquals(0, turns.turnsOf(other))
    }

    @Test
    fun `observers see the turns of every photo`() {
        turns.turn(photo, 1)
        turns.turn(other, 2)

        assertEquals(mapOf(photo to 1, other to 2), turns.turns.value)
    }

    private class Saver(var result: TurnSaveResult = TurnSaveResult.Saved, var during: () -> Unit = {}) {
        val calls = mutableListOf<Triple<FileHandle, String, Int>>()

        fun save(handle: FileHandle, name: String, quarterTurns: Int): TurnSaveResult {
            calls += Triple(handle, name, quarterTurns)
            during()
            return result
        }
    }

    private val saver = Saver()
    private val saving = ViewTurns(saver::save)

    @Test
    fun `a photo nobody saved has revision 0`() = assertEquals(0, saving.revisionOf(photo))

    @Test
    fun `saving hands the pending turn to the saver and clears it`() {
        saving.turn(photo, 3)

        assertEquals(TurnSaveResult.Saved, saving.save(photo, "a.jpg"))

        assertEquals(listOf(Triple(photo, "a.jpg", 3)), saver.calls)
        assertEquals(0, saving.turnsOf(photo))
        assertTrue(saving.turns.value.isEmpty())
    }

    @Test
    fun `a save that comes with a note counts as saved and returns the note`() {
        val note = TurnSaveResult.SavedWithNote("look here")
        val noting = ViewTurns(Saver(note)::save)
        noting.turn(photo, 1)

        assertEquals(note, noting.save(photo, "a.heic"))

        assertEquals(0, noting.turnsOf(photo))
        assertEquals(1, noting.revisionOf(photo))
    }

    @Test
    fun `a saved photo's revision goes up each time`() {
        saving.turn(photo, 1)
        saving.save(photo, "a.jpg")
        assertEquals(1, saving.revisionOf(photo))

        saving.turn(photo, 1)
        saving.save(photo, "a.jpg")
        assertEquals(2, saving.revisionOf(photo))
        assertEquals(mapOf(photo to 2), saving.revisions.value)
        assertEquals(0, saving.revisionOf(other))
    }

    /** A view must never draw the old, still sideways picture without its turn: the revision comes first. */
    @Test
    fun `the revision is up before the turn is taken off`() {
        saving.turn(photo, 1)
        var revisionWhenCleared: Int? = null
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            saving.turns.collect { if (it.isEmpty()) revisionWhenCleared = saving.revisionOf(photo) }
        }

        saving.save(photo, "a.jpg")
        job.cancel()

        assertEquals(1, revisionWhenCleared)
    }

    @Test
    fun `a turn made while the save runs is kept`() {
        saving.turn(photo, 1)
        saver.during = { saving.turn(photo, 1) }

        saving.save(photo, "a.jpg")

        assertEquals(1, saving.turnsOf(photo))
    }

    @Test
    fun `a refused or failed save keeps the turn and the revision`() {
        for (result in listOf(TurnSaveResult.Refused("no"), TurnSaveResult.Failed("busy"))) {
            saver.result = result
            saving.turn(photo, 1)

            assertEquals(result, saving.save(photo, "a.jpg"))

            assertEquals(1, saving.turnsOf(photo))
            assertEquals(0, saving.revisionOf(photo))
            saving.turn(photo, -1)
        }
    }

    @Test
    fun `saving a photo that is not turned asks nothing and changes nothing`() {
        assertEquals(TurnSaveResult.Saved, saving.save(photo, "a.jpg"))

        assertTrue(saver.calls.isEmpty())
        assertEquals(0, saving.revisionOf(photo))
    }

    @Test
    fun `without a saver a save is refused`() {
        turns.turn(photo, 1)

        assertEquals(TurnSaveResult.Refused(ViewTurns.NO_SAVER), turns.save(photo, "a.jpg"))

        assertEquals(1, turns.turnsOf(photo))
    }
}
