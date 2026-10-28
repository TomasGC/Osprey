package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationTest {

    @Test
    fun `values 1 to 8 map to their orientation`() {
        val expected = listOf(
            ExifOrientation.NORMAL, ExifOrientation.FLIP_HORIZONTAL, ExifOrientation.ROTATE_180,
            ExifOrientation.FLIP_VERTICAL, ExifOrientation.TRANSPOSE, ExifOrientation.ROTATE_90,
            ExifOrientation.TRANSVERSE, ExifOrientation.ROTATE_270,
        )
        assertEquals(expected, (1..8).map { ExifOrientation.fromExif(it) })
    }

    @Test
    fun `a missing or out of range value is normal`() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExif(null))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExif(0))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExif(9))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExif(-3))
    }

    @Test
    fun `clockwise degrees map to the quarter turns`() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromClockwiseDegrees(0))
        assertEquals(ExifOrientation.ROTATE_90, ExifOrientation.fromClockwiseDegrees(90))
        assertEquals(ExifOrientation.ROTATE_180, ExifOrientation.fromClockwiseDegrees(180))
        assertEquals(ExifOrientation.ROTATE_270, ExifOrientation.fromClockwiseDegrees(270))
    }

    @Test
    fun `a missing or odd rotation is normal`() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromClockwiseDegrees(null))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromClockwiseDegrees(45))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromClockwiseDegrees(360))
    }

    @Test
    fun `only the four diagonal and quarter turn values swap width and height`() {
        val swapping = ExifOrientation.entries.filter { it.swapsDimensions }
        assertEquals(
            listOf(
                ExifOrientation.TRANSPOSE, ExifOrientation.ROTATE_90,
                ExifOrientation.TRANSVERSE, ExifOrientation.ROTATE_270,
            ),
            swapping,
        )
        assertFalse(ExifOrientation.NORMAL.swapsDimensions)
        assertTrue(ExifOrientation.ROTATE_90.swapsDimensions)
    }

    @Test
    fun `one clockwise turn follows the exif table`() {
        val expected = listOf(6, 7, 8, 5, 2, 3, 4, 1).map { ExifOrientation.fromExif(it) }
        assertEquals(expected, ExifOrientation.entries.map { it.turnedBy(1) })
    }

    /** Turning the stored orientation must give the pixels that turning the shown photo gives, for all 8 and 4 turns. */
    @Test
    fun `turning the tag turns the shown photo`() {
        val source = PixelBuffer(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))
        val rotations = listOf(
            ExifOrientation.NORMAL, ExifOrientation.ROTATE_90, ExifOrientation.ROTATE_180, ExifOrientation.ROTATE_270,
        )
        for (orientation in ExifOrientation.entries) {
            for (turns in -5..5) {
                val shownThenTurned = source.oriented(orientation).oriented(rotations[turns.mod(4)])
                assertEquals("$orientation x $turns", shownThenTurned, source.oriented(orientation.turnedBy(turns)))
            }
        }
    }
}
