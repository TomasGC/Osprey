package app.osprey.media

import app.osprey.filesystem.FakeFileSystemRepository
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
}
