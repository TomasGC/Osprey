package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PixelBufferTest {

    @Test
    fun `two buffers with identical pixel content are equal and hash the same`() {
        val a = PixelBuffer(2, 1, intArrayOf(1, 2))
        val b = PixelBuffer(2, 1, intArrayOf(1, 2))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `buffers differing only by pixel content are not equal`() {
        val a = PixelBuffer(2, 1, intArrayOf(1, 2))
        val b = PixelBuffer(2, 1, intArrayOf(1, 3))

        assertNotEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructing with a pixel count that does not match width times height throws`() {
        PixelBuffer(2, 2, intArrayOf(1, 2, 3))
    }
}
