package app.osprey.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a turned png or heic says only jpeg can be saved`() {
        assertEquals(TurnSaver.NOT_A_JPEG, saveTurnHint("a.png", 1))
        assertEquals(TurnSaver.NOT_A_JPEG, saveTurnHint("a.heic", 3))
    }

    @Test
    fun `a photo that is not turned has no hint whatever its format`() {
        assertNull(saveTurnHint("a.png", 0))
        assertNull(saveTurnHint("a.jpg", 0))
    }
}
