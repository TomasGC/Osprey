package app.osprey.desktop

import app.osprey.desktop.media.extractVideoFramePng
import app.osprey.desktop.media.probeVideoCreationTime
import app.osprey.desktop.testing.TimedTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

private val SHORT: Duration = Duration.ofSeconds(2)

/**
 * A real `ffmpeg` and `ffprobe` that never finish: a named pipe nobody writes to blocks them as they open it. Named
 * pipes of this kind exist on Linux, where CI runs this tier.
 */
class HungVideoRealTest : TimedTest() {

    private lateinit var folder: Path

    @Before
    fun setUp() {
        assumeFalse("no mkfifo on Windows", System.getProperty("os.name").startsWith("Windows"))
        folder = Files.createTempDirectory("osprey-hung-video")
    }

    @After
    fun tearDown() {
        if (::folder.isInitialized) folder.toFile().deleteRecursively()
    }

    private fun stuckVideo(): Path {
        val pipe = folder.resolve("stuck.mp4")
        assertEquals(0, ProcessBuilder("mkfifo", pipe.toString()).start().waitFor())
        return pipe
    }

    @Test
    fun `a frame that ffmpeg never delivers is given up at the timeout`() {
        assertNull(extractVideoFramePng(stuckVideo(), SHORT))
    }

    @Test
    fun `a creation time that ffprobe never delivers is given up at the timeout`() {
        assertNull(probeVideoCreationTime(stuckVideo(), SHORT))
    }
}
