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
}
