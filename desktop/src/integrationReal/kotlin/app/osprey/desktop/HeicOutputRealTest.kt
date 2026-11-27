package app.osprey.desktop

import app.osprey.desktop.media.HeicOutput
import app.osprey.desktop.media.heicOutputFor
import app.osprey.desktop.media.probeHeicGroups
import app.osprey.desktop.testing.TimedTest
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The choice of picture format from what a real `ffprobe` says about generated 8, 10 and 12-bit pictures. */
class HeicOutputRealTest : TimedTest() {

    private lateinit var folder: Path

    @Before
    fun setUp() {
        folder = Files.createTempDirectory("osprey-heic-output")
    }

    @After
    fun tearDown() {
        folder.toFile().deleteRecursively()
    }

    private fun picture(pixelFormat: String): Path {
        val file = folder.resolve("$pixelFormat.mp4")
        val command = listOf(
            "ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc2=s=64x64:d=1", "-frames:v", "1",
            "-pix_fmt", pixelFormat, "-c:v", "libx265", "-x265-params", "log-level=none", file.toString(),
        )
        assertEquals(0, ProcessBuilder(command).inheritIO().start().waitFor())
        return file
    }

    @Test
    fun `an 8-bit picture is written as BMP and a deeper one as PNG`() {
        assertEquals(HeicOutput.BMP, heicOutputFor(probeHeicGroups(picture("yuv420p"))))
        assertEquals(HeicOutput.PNG, heicOutputFor(probeHeicGroups(picture("yuv420p10le"))))
        assertEquals(HeicOutput.PNG, heicOutputFor(probeHeicGroups(picture("yuv420p12le"))))
    }
}
