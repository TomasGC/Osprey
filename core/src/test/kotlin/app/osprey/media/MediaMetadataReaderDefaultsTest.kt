package app.osprey.media

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertNull
import org.junit.Test

/** A reader that only does what the interface demands says no to the optional region calls (#60). */
class MediaMetadataReaderDefaultsTest {

    private val reader = object : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null
        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? = null
    }
    private val handle = FakeFileSystemRepository().let { it.addFile(it.root, "photo.jpg") }

    @Test
    fun `the image size is unknown`() = assertNull(reader.imageSize(handle))

    @Test
    fun `a region cannot be decoded`() = assertNull(reader.decodeRegion(handle, PhotoRegion(0, 0, 1, 1), 1))
}
