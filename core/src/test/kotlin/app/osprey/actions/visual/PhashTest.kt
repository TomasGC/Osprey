package app.osprey.actions.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhashTest {

    @Test
    fun `identical pixel buffers hash the same`() {
        val a = noisePixelBuffer(seed = 1)
        val b = noisePixelBuffer(seed = 1)

        assertEquals(phash(a), phash(b))
    }

    @Test
    fun `structurally different pixel buffers hash differently`() {
        val a = noisePixelBuffer(seed = 1)
        val b = noisePixelBuffer(seed = 2)

        assertNotEquals(phash(a), phash(b))
    }
}
