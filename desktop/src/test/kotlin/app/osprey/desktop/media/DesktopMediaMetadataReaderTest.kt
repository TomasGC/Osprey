package app.osprey.desktop.media

import app.osprey.desktop.filesystem.PathHandle
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DesktopMediaMetadataReaderTest {

    private lateinit var tempDir: Path
    private val reader = DesktopMediaMetadataReader()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-reader-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun file(name: String) = PathHandle(Files.write(tempDir.resolve(name), byteArrayOf(1, 2, 3)))

    @Test
    fun `a file that is neither an image nor a video has no capture date`() {
        assertNull(reader.captureDateTime(file("notes.txt")))
    }

    @Test
    fun `a file that is neither an image nor a video has no frame`() {
        assertNull(reader.decodeFrame(file("notes.txt"), maxSide = null))
        assertNull(reader.decodeFrame(file("README"), maxSide = 128))
    }

    @Test
    fun `an image that cannot be read has no capture date and no frame`() {
        val broken = file("broken.JPG")

        assertNull(reader.captureDateTime(broken))
        assertNull(reader.decodeFrame(broken, maxSide = null))
    }
}
