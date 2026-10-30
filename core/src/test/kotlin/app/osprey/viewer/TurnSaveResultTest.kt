package app.osprey.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnSaveResultTest {

    @Test
    fun `a save that worked says nothing`() = assertNull(TurnSaveResult.Saved.userMessage)

    @Test
    fun `a refusal says its reason`() =
        assertEquals("Only JPEG", TurnSaveResult.Refused("Only JPEG").userMessage)

    @Test
    fun `a failure says it could not save and why`() =
        assertEquals("Could not save the turn: busy", TurnSaveResult.Failed("busy").userMessage)

    @Test
    fun `a turned jpeg has no hint`() = assertNull(saveTurnHint("a.jpg", 1))

    @Test
    fun `a turned png has no hint`() = assertNull(saveTurnHint("a.png", 1))

    @Test
    fun `a turned heic or heif has no hint`() {
        assertNull(saveTurnHint("a.heic", 3))
        assertNull(saveTurnHint("a.HEIF", 1))
    }

    @Test
    fun `a turned format that cannot be saved says which can`() {
        assertEquals("Only JPEG, PNG and HEIC photos can be saved turned for now", TurnSaver.UNSUPPORTED_FORMAT)
        assertEquals(TurnSaver.UNSUPPORTED_FORMAT, saveTurnHint("a.gif", 3))
        assertEquals(TurnSaver.UNSUPPORTED_FORMAT, saveTurnHint("a", 1))
    }

    @Test
    fun `a save with a note says the note`() =
        assertEquals("Saved. Look here", TurnSaveResult.SavedWithNote("Saved. Look here").userMessage)

    @Test
    fun `saved and saved with a note both count as saved, the others do not`() {
        assertTrue(TurnSaveResult.Saved.isSaved)
        assertTrue(TurnSaveResult.SavedWithNote("n").isSaved)
        assertFalse(TurnSaveResult.Refused("r").isSaved)
        assertFalse(TurnSaveResult.Failed("f").isSaved)
    }

    @Test
    fun `a photo that is not turned has no hint whatever its format`() {
        assertNull(saveTurnHint("a.png", 0))
        assertNull(saveTurnHint("a.heic", 0))
        assertNull(saveTurnHint("a.jpg", 0))
    }
}
