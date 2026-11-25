package app.osprey.filesystem

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMoveTest {

    private val steps = mutableListOf<String>()
    private var moveFails = false
    private var lastRenameFails = false
    private var giveBackFails = false

    private val move = ProviderMove(
        renameSource = {
            check(!(giveBackFails && it == "a.jpg")) { "locked" }
            steps += "source $it"
        },
        move = {
            if (moveFails) throw IOException("no space")
            steps += "move"
        },
        renameMoved = {
            check(!lastRenameFails) { "locked" }
            steps += "moved $it"
        },
    )

    @Test
    fun `a move that keeps the name renames nothing`() {
        move.run("a.jpg", "a.jpg", takenInSource = { false })

        assertEquals(listOf("move"), steps)
    }

    // Looking is a listing of the whole source folder: too slow to pay on every move of a large one.
    @Test
    fun `a move that keeps the name never looks at the source folder`() {
        var looked = false

        move.run("a.jpg", "a.jpg") {
            looked = true
            false
        }

        assertFalse(looked)
    }

    @Test
    fun `a new name free in the source folder is taken before the move`() {
        move.run("a.jpg", "b.jpg", takenInSource = { false })

        assertEquals(listOf("source b.jpg", "move"), steps)
    }

    @Test
    fun `a new name taken in the source folder is taken after the move`() {
        move.run("a.jpg", "b.jpg", takenInSource = { true })

        val temporary = steps.first().removePrefix("source ")
        assertTrue(temporary, temporary.startsWith("b.osprey-rename-") && temporary.endsWith(".jpg"))
        assertEquals(listOf("source $temporary", "move", "moved b.jpg"), steps)
    }

    @Test
    fun `a failed move gives the source its name back`() {
        moveFails = true

        assertThrows(IOException::class.java) { move.run("a.jpg", "b.jpg", takenInSource = { false }) }

        assertEquals(listOf("source b.jpg", "source a.jpg"), steps)
    }

    @Test
    fun `a failed move that cannot give the name back says which name the file is left under`() {
        moveFails = true
        giveBackFails = true

        val failure = assertThrows(IllegalStateException::class.java) {
            move.run("a.jpg", "b.jpg", takenInSource = { false })
        }

        assertEquals("move failed and the file is left as b.jpg: locked", failure.message)
    }

    @Test
    fun `a failed move that renamed nothing has nothing to give back`() {
        moveFails = true

        assertThrows(IOException::class.java) { move.run("a.jpg", "a.jpg", takenInSource = { false }) }

        assertEquals(emptyList<String>(), steps)
    }

    @Test
    fun `a last rename that fails says the name the moved file kept`() {
        lastRenameFails = true

        val failure = assertThrows(IllegalStateException::class.java) {
            move.run("a.jpg", "b.jpg", takenInSource = { true })
        }

        val temporary = steps.first().removePrefix("source ")
        assertEquals("moved, but kept the name $temporary: locked", failure.message)
    }
}
