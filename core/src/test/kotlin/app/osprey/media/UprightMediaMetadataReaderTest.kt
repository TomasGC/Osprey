package app.osprey.media

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UprightMediaMetadataReaderTest {

    private val handle = FakeFileSystemRepository().let { it.addFile(it.root, "photo.jpg") }
    private val inner = FakeMediaMetadataReader()
    private val frame = PixelBuffer(2, 1, intArrayOf(1, 2))

    @Test
    fun `a frame is returned turned by its orientation`() {
        inner.setFrame(handle, frame)
        val reader = UprightMediaMetadataReader(inner) { ExifOrientation.ROTATE_90 }

        assertEquals(PixelBuffer(1, 2, intArrayOf(1, 2)), reader.decodeFrame(handle))
    }

    @Test
    fun `an upright photo comes back as decoded`() {
        inner.setFrame(handle, frame)
        val reader = UprightMediaMetadataReader(inner) { ExifOrientation.NORMAL }

        assertSame(frame, reader.decodeFrame(handle))
    }

    @Test
    fun `maxSide goes through to the decoder`() {
        inner.setFrame(handle, frame)
        UprightMediaMetadataReader(inner) { ExifOrientation.NORMAL }.decodeFrame(handle, 128)

        assertEquals(listOf<Int?>(128), inner.requestedMaxSides)
    }

    @Test
    fun `a photo that cannot be decoded is null and its orientation is never read`() {
        var asked = 0
        val reader = UprightMediaMetadataReader(inner) {
            asked++
            ExifOrientation.ROTATE_90
        }

        assertNull(reader.decodeFrame(handle))
        assertEquals(0, asked)
    }

    @Test
    fun `the capture date is the decoder's`() {
        val date = MediaDate(LocalDateTime.of(2026, 1, 2, 3, 4, 5), MediaDateSource.EXIF)
        inner.setCaptureDateTime(handle, date)

        assertEquals(date, UprightMediaMetadataReader(inner) { ExifOrientation.NORMAL }.captureDateTime(handle))
    }

    private val stored = PixelBuffer(3, 4, IntArray(12) { it + 1 })

    @Test
    fun `the image size is the upright one`() {
        inner.setFrame(handle, stored)
        val normal = UprightMediaMetadataReader(inner) { ExifOrientation.NORMAL }
        val rotated = UprightMediaMetadataReader(inner) { ExifOrientation.ROTATE_90 }

        assertEquals(PhotoSize(3, 4), normal.imageSize(handle))
        assertEquals(PhotoSize(4, 3), rotated.imageSize(handle))
    }

    @Test
    fun `an unknown size is null and the orientation is never read`() {
        var asked = 0
        val reader = UprightMediaMetadataReader(inner) {
            asked++
            ExifOrientation.ROTATE_90
        }

        assertNull(reader.imageSize(handle))
        assertNull(reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 1))
        assertEquals(0, asked)
    }

    @Test
    fun `a region of the upright photo is decoded from the stored pixels and turned upright, for the 8 orientations`() {
        inner.setFrame(handle, stored)
        for (orientation in ExifOrientation.entries) {
            val upright = stored.oriented(orientation)
            val region = PhotoRegion(1, 0, upright.width - 1, upright.height - 1)

            val tile = UprightMediaMetadataReader(inner) { orientation }.decodeRegion(handle, region, 1)

            assertEquals(orientation.name, upright.cropped(region), tile)
        }
    }

    @Test
    fun `the decoder is asked for the stored region and the same sample size`() {
        inner.setFrame(handle, stored)
        val reader = UprightMediaMetadataReader(inner) { ExifOrientation.ROTATE_90 }

        reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 2)

        assertEquals(listOf(PhotoRegion(0, 3, 1, 1) to 2), inner.requestedRegions)
    }

    @Test
    fun `a decoder that knows the size but cannot decode a region gives null`() {
        inner.setFrame(handle, stored)
        val noRegions = object : MediaMetadataReader by inner {
            override fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer? = null
        }
        val reader = UprightMediaMetadataReader(noRegions) { ExifOrientation.ROTATE_90 }

        assertNull(reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 1))
    }
}
