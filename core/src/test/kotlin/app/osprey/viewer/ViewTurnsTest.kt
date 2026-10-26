package app.osprey.viewer

import app.osprey.filesystem.FakeFileSystemRepository
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
}
