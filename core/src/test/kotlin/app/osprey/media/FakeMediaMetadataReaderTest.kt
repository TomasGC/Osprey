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

    @Test
    fun `imageSize and decodeRegion return null when nothing was registered for the entry`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "photo.jpg")
        val reader = FakeMediaMetadataReader()

        assertNull(reader.imageSize(entry))
        assertNull(reader.decodeRegion(entry, PhotoRegion(0, 0, 1, 1), 1))
    }

    @Test
    fun `imageSize and decodeRegion answer from the registered frame and record the request`() {
        val fs = FakeFileSystemRepository()
        val entry = fs.addFile(fs.root, "photo.jpg")
        val reader = FakeMediaMetadataReader()
        reader.setFrame(entry, PixelBuffer(3, 2, intArrayOf(1, 2, 3, 4, 5, 6)))

        assertEquals(PhotoSize(3, 2), reader.imageSize(entry))
        assertEquals(
            PixelBuffer(2, 1, intArrayOf(5, 6)),
            reader.decodeRegion(entry, PhotoRegion(1, 1, 2, 1), 4),
        )
        assertEquals(listOf(PhotoRegion(1, 1, 2, 1) to 4), reader.requestedRegions)
    }
}
