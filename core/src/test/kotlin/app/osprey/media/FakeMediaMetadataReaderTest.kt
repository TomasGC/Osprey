package app.osprey.media

import app.osprey.filesystem.FakeFileSystemRepository
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeMediaMetadataReaderTest {

    @Test
    fun `captureDateTime returns null when nothing was registered for the entry`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "photo.jpg")

        assertNull(FakeMediaMetadataReader().captureDateTime(entry))
    }

    @Test
    fun `captureDateTime returns exactly what was registered`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "photo.jpg")
        val reader = FakeMediaMetadataReader()
        val date = MediaDate(LocalDateTime.of(2020, 6, 15, 10, 30, 0), MediaDateSource.EXIF)

        reader.setCaptureDateTime(entry, date)

        assertEquals(date, reader.captureDateTime(entry))
    }

    @Test
    fun `decodeFrame returns null when nothing was registered for the entry`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "video.mp4")

        assertNull(FakeMediaMetadataReader().decodeFrame(entry))
    }

    @Test
    fun `decodeFrame returns exactly what was registered`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "video.mp4")
        val reader = FakeMediaMetadataReader()
        val frame = PixelBuffer(2, 1, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))

        reader.setFrame(entry, frame)

        assertEquals(frame, reader.decodeFrame(entry))
    }
}
