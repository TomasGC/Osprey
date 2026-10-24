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

    @Test
    fun `a buffer is never equal to something that is not a buffer`() {
        assertNotEquals(PixelBuffer(1, 1, intArrayOf(1)), "not a buffer")
        assertNotEquals(PixelBuffer(1, 1, intArrayOf(1)), null)
    }

    @Test
    fun `buffers with the same pixels but another shape are not equal`() {
        val wide = PixelBuffer(3, 2, IntArray(6))
        val tall = PixelBuffer(2, 3, IntArray(6))

        assertNotEquals(wide, tall)
        assertNotEquals(wide.hashCode(), tall.hashCode())
    }

    @Test
    fun `a buffer with another width and the same height is not equal`() {
        assertNotEquals(PixelBuffer(2, 1, IntArray(2)), PixelBuffer(3, 1, IntArray(3)))
    }

    @Test
    fun `toString shows the size and not the pixels`() {
        assertEquals("PixelBuffer(width=2, height=3)", PixelBuffer(2, 3, IntArray(6)).toString())
    }

    @Test
    fun `a buffer with the same width and another height is not equal`() {
        assertNotEquals(PixelBuffer(2, 1, IntArray(2)), PixelBuffer(2, 2, IntArray(4)))
    }
}
