package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A 2 wide, 3 high buffer (rows `1 2`, `3 4`, `5 6`): asymmetric both ways, so every orientation looks different. */
class PixelBufferOrientedTest {

    private val source = PixelBuffer(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))

    private fun assertOriented(orientation: ExifOrientation, width: Int, height: Int, vararg pixels: Int) {
        assertEquals(PixelBuffer(width, height, pixels), source.oriented(orientation))
    }

    @Test
    fun `normal returns the same buffer`() = assertSame(source, source.oriented(ExifOrientation.NORMAL))

    @Test
    fun `flip horizontal mirrors each row`() =
        assertOriented(ExifOrientation.FLIP_HORIZONTAL, 2, 3, 2, 1, 4, 3, 6, 5)

    @Test
    fun `rotate 180 reverses the pixels`() = assertOriented(ExifOrientation.ROTATE_180, 2, 3, 6, 5, 4, 3, 2, 1)

    @Test
    fun `flip vertical mirrors the rows`() = assertOriented(ExifOrientation.FLIP_VERTICAL, 2, 3, 5, 6, 3, 4, 1, 2)

    @Test
    fun `transpose swaps rows and columns`() = assertOriented(ExifOrientation.TRANSPOSE, 3, 2, 1, 3, 5, 2, 4, 6)

    @Test
    fun `rotate 90 turns clockwise`() = assertOriented(ExifOrientation.ROTATE_90, 3, 2, 5, 3, 1, 6, 4, 2)

    @Test
    fun `transverse is the anti-diagonal flip`() = assertOriented(ExifOrientation.TRANSVERSE, 3, 2, 6, 4, 2, 5, 3, 1)

    @Test
    fun `rotate 270 turns counterclockwise`() = assertOriented(ExifOrientation.ROTATE_270, 3, 2, 2, 4, 6, 1, 3, 5)
}
