package app.osprey.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A 3 wide, 4 high stored buffer with 12 different pixels, so a wrong corner shows up as a wrong value. */
class PhotoRegionTest {

    private val stored = PixelBuffer(3, 4, IntArray(12) { it + 1 })

    @Test
    fun `cropped keeps the pixels inside the region`() =
        assertEquals(PixelBuffer(2, 2, intArrayOf(5, 6, 8, 9)), stored.cropped(PhotoRegion(1, 1, 2, 2)))

    @Test
    fun `cropped refuses a region that leaves the buffer`() {
        assertThrows(IllegalArgumentException::class.java) { stored.cropped(PhotoRegion(2, 0, 2, 1)) }
        assertThrows(IllegalArgumentException::class.java) { stored.cropped(PhotoRegion(0, 3, 1, 2)) }
    }

    @Test
    fun `a region needs a position inside the photo and a size`() {
        assertThrows(IllegalArgumentException::class.java) { PhotoRegion(-1, 0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRegion(0, -1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRegion(0, 0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoRegion(0, 0, 1, 0) }
    }

    @Test
    fun `a normal photo keeps its region`() =
        assertEquals(PhotoRegion(1, 2, 2, 1), PhotoRegion(1, 2, 2, 1).toStored(ExifOrientation.NORMAL, 3, 4))

    @Test
    fun `the whole upright photo maps to the whole stored photo for the 8 orientations`() {
        for (orientation in ExifOrientation.entries) {
            val upright = stored.oriented(orientation)
            val whole = PhotoRegion(0, 0, upright.width, upright.height)
            assertEquals(
                orientation.name,
                PhotoRegion(0, 0, 3, 4),
                whole.toStored(orientation, upright.width, upright.height),
            )
        }
    }

    @Test
    fun `the top left upright pixel of a rotate 90 photo is the bottom left stored pixel`() =
        assertEquals(PhotoRegion(0, 3, 1, 1), PhotoRegion(0, 0, 1, 1).toStored(ExifOrientation.ROTATE_90, 4, 3))

    @Test
    fun `the top left upright pixel of a rotate 270 photo is the top right stored pixel`() =
        assertEquals(PhotoRegion(2, 0, 1, 1), PhotoRegion(0, 0, 1, 1).toStored(ExifOrientation.ROTATE_270, 4, 3))

    /** Every region of every upright size: cutting the stored photo, then turning it, equals turning, then cutting. */
    @Test
    fun `a region maps to the stored pixels that show it, for every region and the 8 orientations`() {
        for (orientation in ExifOrientation.entries) {
            val upright = stored.oriented(orientation)
            for (region in allRegions(upright.width, upright.height)) {
                val viaStored = stored
                    .cropped(region.toStored(orientation, upright.width, upright.height))
                    .oriented(orientation)
                assertEquals("$orientation $region", upright.cropped(region), viaStored)
            }
        }
    }

    private fun allRegions(width: Int, height: Int) = buildList {
        for (left in 0 until width) for (top in 0 until height) {
            for (w in 1..width - left) for (h in 1..height - top) add(PhotoRegion(left, top, w, h))
        }
    }
}
