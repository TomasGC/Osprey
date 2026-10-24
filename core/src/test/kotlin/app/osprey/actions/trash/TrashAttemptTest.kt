package app.osprey.actions.trash

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TrashAttemptTest {

    private fun failureOf(error: Throwable): TrashFailure? = attempt("Trash/2019/a.jpg") { throw error }

    @Test
    fun `an action that works has no failure`() {
        var ran = false

        assertNull(attempt("Trash/2019/a.jpg") { ran = true })
        assertEquals(true, ran)
    }

    @Test
    fun `an I-O error becomes a failure carrying its message`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", "disk full"), failureOf(IOException("disk full")))
    }

    @Test
    fun `a name that is already taken becomes a failure`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", "a.jpg exists"), failureOf(FileAlreadyExistsException("a.jpg exists")))
    }

    @Test
    fun `a folder that is not empty becomes a failure`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", "not empty"), failureOf(DirectoryNotEmptyException("not empty")))
    }

    @Test
    fun `a state error becomes a failure`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", "gone"), failureOf(IllegalStateException("gone")))
    }

    @Test
    fun `a refused permission becomes a failure`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", "denied"), failureOf(SecurityException("denied")))
    }

    @Test
    fun `an error without a message gives an empty reason`() {
        assertEquals(TrashFailure("Trash/2019/a.jpg", ""), failureOf(IOException()))
    }

    @Test
    fun `an error that is not a file system error is not swallowed`() {
        try {
            failureOf(UnsupportedOperationException("bug"))
            fail("expected the exception to propagate")
        } catch (expected: UnsupportedOperationException) {
            assertEquals("bug", expected.message)
        }
    }
}
