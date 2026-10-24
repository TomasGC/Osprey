package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SubsampleStepTest {

    @Test
    fun `a source already within maxSide is not subsampled`() {
        assertEquals(1, subsampleStepFor(width = 100, height = 80, maxSide = 128))
    }

    @Test
    fun `a 4000x3000 photo at 128 is subsampled 16 times, leaving 250 px`() {
        assertEquals(16, subsampleStepFor(width = 4000, height = 3000, maxSide = 128))
    }

    @Test
    fun `the longest side decides, not the width`() {
        assertEquals(16, subsampleStepFor(width = 3000, height = 4000, maxSide = 128))
    }

    @Test
    fun `the result is a power of two and never leaves less than maxSide`() {
        for (side in listOf(129, 255, 256, 257, 1000, 4032, 8000)) {
            val step = subsampleStepFor(width = side, height = side, maxSide = 128)

            assertEquals("power of two for $side", 1, Integer.bitCount(step))
            assertEquals("at least maxSide for $side", true, side / step >= 128)
        }
    }

    @Test
    fun `a source just under twice maxSide is not subsampled`() {
        assertEquals(1, subsampleStepFor(width = 255, height = 100, maxSide = 128))
    }
}
