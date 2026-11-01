package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Test

class HeicRotationTest {

    @Test
    fun `the quarter turns are the irot angle of the primary item`() {
        for (angle in 0..3) {
            assertEquals("irot $angle", angle, HeicRotation.of(heic(primaryIrot = angle).bytes))
        }
    }

    @Test
    fun `only the low two bits of the irot byte count`() {
        assertEquals(2, HeicRotation.of(heic(primaryIrot = 6).bytes))
    }

    @Test
    fun `a photo with no irot on its primary item is not turned, whatever its tiles say`() {
        assertEquals(0, HeicRotation.of(heic(primaryIrot = null).bytes))
        assertEquals(0, HeicRotation.of(heic(primaryIrot = null, tileIrot = 3).bytes))
    }

    @Test
    fun `the samples report the angle they were built with`() {
        assertEquals(3, HeicRotation.of(HeicSamples.rotated.bytes))
        assertEquals(0, HeicRotation.of(HeicSamples.plain.bytes))
    }

    @Test
    fun `a file that is not a readable HEIC is not turned`() {
        assertEquals(0, HeicRotation.of(ByteArray(40)))
        assertEquals(0, HeicRotation.of(heic(twoPrimaryIrot = true).bytes))
    }
}
