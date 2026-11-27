package app.osprey.desktop.media

import app.osprey.desktop.testing.TimedTest
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameCommandTest : TimedTest() {

    private val video = Path.of("my videos", "MOV 1.mp4")
    private val command = videoFrameCommand(video)

    @Test
    fun `the command reads one frame a second in and writes it as PNG on stdout`() {
        assertEquals("ffmpeg", command.first())
        assertEquals("1", command[command.indexOf("-ss") + 1])
        assertEquals(video.toString(), command[command.indexOf("-i") + 1])
        assertEquals("1", command[command.indexOf("-frames:v") + 1])
        assertEquals("png", command[command.indexOf("-vcodec") + 1])
        assertTrue("-nostdin" in command)
        assertEquals("-", command.last())
    }

    @Test
    fun `the decoder gets four threads at most, said before the input it applies to`() {
        val threads = command.indexOf("-threads")

        assertTrue("$command", threads in 0 until command.indexOf("-i"))
        assertEquals("4", command[threads + 1])
    }
}
