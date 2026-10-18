package app.osprey.actions.dedupe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FileHashTest {

    @Test
    fun `identical content hashes the same`() {
        val a = "same content".toByteArray()
        val b = "same content".toByteArray()

        assertEquals(fileHash(a), fileHash(b))
    }

    @Test
    fun `different content hashes differently`() {
        val a = "content one".toByteArray()
        val b = "content two".toByteArray()

        assertNotEquals(fileHash(a), fileHash(b))
    }
}
