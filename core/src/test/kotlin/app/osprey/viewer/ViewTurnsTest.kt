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

    @Test
    fun `a photo is being saved only while the saver runs`() {
        saving.turn(photo, 1)
        val during = mutableListOf<Set<FileHandle>>()
        saver.during = { during += saving.savingNow.value }

        assertTrue(saving.savingNow.value.isEmpty())
        saving.save(photo, "a.jpg")

        assertEquals(listOf(setOf(photo)), during)
        assertTrue(saving.savingNow.value.isEmpty())
    }

    @Test
    fun `a refused or failed save stops being a save in flight`() {
        for (result in listOf(TurnSaveResult.Refused("no"), TurnSaveResult.Failed("busy"))) {
            saver.result = result
            saving.turn(photo, 1)

            saving.save(photo, "a.jpg")

            assertTrue(saving.savingNow.value.isEmpty())
            saving.turn(photo, -1)
        }
    }

    @Test
    fun `a second save of a photo already being saved is refused and writes nothing`() {
        saving.turn(photo, 1)
        var second: TurnSaveResult? = null
        saver.during = { second = saving.save(photo, "a.jpg") }

        saving.save(photo, "a.jpg")

        assertEquals(TurnSaveResult.Refused(ViewTurns.ALREADY_SAVING), second)
        assertEquals(1, saver.calls.size)
        assertTrue(saving.saveMessages.value.isEmpty())
    }

    @Test
    fun `another photo can be saved while one is being saved`() {
        saving.turn(photo, 1)
        saving.turn(other, 1)
        var inner: Set<FileHandle>? = null
        saver.during = {
            saver.during = { inner = saving.savingNow.value }
            saving.save(other, "b.jpg")
        }

        saving.save(photo, "a.jpg")

        assertEquals(setOf(photo, other), inner)
        assertEquals(2, saver.calls.size)
    }

    @Test
    fun `a saver that throws is a failed save, not a crash`() {
        saving.turn(photo, 1)
        saver.during = { error("disk gone") }

        val result = saving.save(photo, "a.jpg")

        assertEquals(TurnSaveResult.Failed("disk gone"), result)
        assertTrue(saving.savingNow.value.isEmpty())
        assertEquals(1, saving.turnsOf(photo))
        assertEquals(0, saving.revisionOf(photo))
    }

    @Test
    fun `a saver that throws without a message is named by its error`() {
        saving.turn(photo, 1)
        saver.during = { throw IllegalStateException() }

        assertEquals(TurnSaveResult.Failed("IllegalStateException"), saving.save(photo, "a.jpg"))
    }

    @Test
    fun `what a failed save said is kept for that photo`() {
        saver.result = TurnSaveResult.Failed("busy")
        saving.turn(photo, 1)

        saving.save(photo, "a.jpg")

        assertEquals(mapOf(photo to "Could not save the turn: busy"), saving.saveMessages.value)
    }

    @Test
    fun `a thrown error is kept like a failed save`() {
        saving.turn(photo, 1)
        saver.during = { error("disk gone") }

        saving.save(photo, "a.jpg")

        assertEquals(mapOf(photo to "Could not save the turn: disk gone"), saving.saveMessages.value)
    }

    @Test
    fun `a refusal and the note of a good save are kept too`() {
        saver.result = TurnSaveResult.Refused("no")
        saving.turn(photo, 1)
        saving.save(photo, "a.jpg")
        saver.result = TurnSaveResult.SavedWithNote("look here")
        saving.turn(other, 1)
        saving.save(other, "b.heic")

        assertEquals(mapOf(photo to "no", other to "look here"), saving.saveMessages.value)
    }

    @Test
    fun `one failed photo does not stop the others and each keeps its own message`() {
        saving.turn(photo, 1)
        saving.turn(other, 1)
        saver.result = TurnSaveResult.Failed("busy")
        saving.save(photo, "a.jpg")
        saver.result = TurnSaveResult.Saved
        saving.save(other, "b.jpg")

        assertEquals(mapOf(photo to "Could not save the turn: busy"), saving.saveMessages.value)
        assertEquals(1, saving.turnsOf(photo))
        assertEquals(0, saving.turnsOf(other))
    }

    @Test
    fun `a later failure replaces the earlier message of the same photo`() {
        saving.turn(photo, 1)
        saver.result = TurnSaveResult.Failed("busy")
        saving.save(photo, "a.jpg")
        saver.result = TurnSaveResult.Failed("full")
        saving.save(photo, "a.jpg")

        assertEquals(mapOf(photo to "Could not save the turn: full"), saving.saveMessages.value)
    }

    @Test
    fun `a save that works clears the photo's message`() {
        saving.turn(photo, 1)
        saver.result = TurnSaveResult.Failed("busy")
        saving.save(photo, "a.jpg")
        saver.result = TurnSaveResult.Saved
        saving.save(photo, "a.jpg")

        assertTrue(saving.saveMessages.value.isEmpty())
    }

    @Test
    fun `turning a photo again clears its message and leaves the others`() {
        saver.result = TurnSaveResult.Failed("busy")
        saving.turn(photo, 1)
        saving.turn(other, 1)
        saving.save(photo, "a.jpg")
        saving.save(other, "b.jpg")

        saving.turn(photo, 1)

        assertEquals(mapOf(other to "Could not save the turn: busy"), saving.saveMessages.value)
    }
}
