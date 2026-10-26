package app.osprey.desktop.media

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FfmpegHeicRendererTest {

    private lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("osprey-heic-renderer-test")
    }

    @After
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `the command renders one frame of the photo as PNG on stdout`() {
        val command = heicRenderCommand(Path.of("my photos", "IMG 1.heic"))

        assertEquals("ffmpeg", command.first())
        assertEquals(Path.of("my photos", "IMG 1.heic").toString(), command[command.indexOf("-i") + 1])
        assertEquals("1", command[command.indexOf("-frames:v") + 1])
        assertEquals("png", command[command.indexOf("-vcodec") + 1])
        assertEquals("image2pipe", command[command.indexOf("-f") + 1])
        assertEquals("-", command.last())
    }

    @Test
    fun `the command asks for the fastest PNG compression and never reads stdin`() {
        val command = heicRenderCommand(Path.of("a.heic"))

        assertEquals("1", command[command.indexOf("-compression_level") + 1])
        assertEquals(true, "-nostdin" in command)
    }

    @Test
    fun `a photo that does not exist renders nothing, with or without ffmpeg installed`() {
        assertNull(FfmpegHeicRenderer.renderPng(tempDir.resolve("missing.heic")))
    }

    @Test
    fun `a file that is not a picture renders nothing`() {
        val broken = Files.write(tempDir.resolve("broken.heic"), byteArrayOf(1, 2, 3))

        assertNull(FfmpegHeicRenderer.renderPng(broken))
    }
}
