package app.osprey.desktop

import app.osprey.desktop.media.runFfmpeg
import app.osprey.desktop.media.videoFrameCommand
import app.osprey.desktop.testing.TimedTest
import app.osprey.fixtures.Photos
import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The frame of a real video does not depend on the limit put on ffmpeg's decoder threads (#87). */
class VideoFrameRealVideosTest : TimedTest() {

    /** The frame as it was asked for before the limit, written out here so the command under test cannot change it. */
    private fun unlimited(video: Path) = listOf(
        "ffmpeg", "-nostdin", "-ss", "1", "-i", video.toString(),
        "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-",
    )

    @Test
    fun `limiting the decoder's threads gives the very frame an unlimited decode gives`() {
        for (video in Photos.mp4(Photos.need())) {
            val frame = runFfmpeg(videoFrameCommand(video))

            assertNotNull("${video.fileName} gave no frame", frame)
            assertArrayEquals("${video.fileName}", runFfmpeg(unlimited(video)), frame)
        }
    }
}
