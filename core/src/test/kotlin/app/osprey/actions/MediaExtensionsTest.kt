package app.osprey.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExtensionsTest {

    @Test
    fun `a HEIC photo is a heic or a heif file`() {
        assertEquals(setOf(".heic", ".heif"), heicExtensions)
    }

    @Test
    fun `the image extensions are jpg, jpeg, png and the HEIC ones`() {
        assertEquals(setOf(".jpg", ".jpeg", ".png", ".heic", ".heif"), imageExtensions)
    }

    @Test
    fun `no image extension is also a video extension`() {
        assertTrue(imageExtensions.intersect(videoExtensions).isEmpty())
    }
}
