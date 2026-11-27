package app.osprey.desktop.media

import app.osprey.desktop.testing.TimedTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HeicOutputTest : TimedTest() {

    private fun streams(vararg formats: String) =
        formats.joinToString(prefix = "[", postfix = "]") { """{"pix_fmt": "$it"}""" }

    private fun plain(vararg formats: String) = """{"stream_groups": [], "streams": ${streams(*formats)}}"""

    private fun grid(tiles: String, file: String) =
        """{"stream_groups": [{"type": "Tile Grid", "streams": $tiles}], "streams": $file}"""

    @Test
    fun `an 8-bit 4-2-0 picture is written as BMP`() {
        assertEquals(HeicOutput.BMP, heicOutputFor(plain("yuv420p")))
        assertEquals(HeicOutput.BMP, heicOutputFor(plain("yuvj420p", "yuvj420p")))
    }

    @Test
    fun `anything deeper or of another kind stays PNG`() {
        for (format in listOf("yuv420p10le", "yuv420p12le", "gray", "yuv444p", "rgba", "")) {
            assertEquals(format, HeicOutput.PNG, heicOutputFor(plain(format)))
            assertEquals(format, HeicOutput.PNG, heicOutputFor(plain("yuv420p", format)))
        }
    }

    @Test
    fun `a tile grid is judged by its own tiles, not by the other streams of the file`() {
        assertEquals(HeicOutput.BMP, heicOutputFor(grid(streams("yuvj420p"), streams("yuvj420p", "gray"))))
        assertEquals(HeicOutput.PNG, heicOutputFor(grid(streams("yuv420p10le"), streams("yuvj420p"))))
    }

    @Test
    fun `a group that is not a tile grid is not the picture`() {
        val other = """{"stream_groups": [{"type": "Other", "streams": ${streams("yuv420p")}}], """ +
            """"streams": ${streams("gray")}}"""

        assertEquals(HeicOutput.PNG, heicOutputFor(other))
    }

    @Test
    fun `a probe that says nothing means PNG`() {
        for (probe in listOf(null, "", "not json", "{}", plain(), """{"streams": [{}]}""", """{"streams": [1]}""")) {
            assertEquals("$probe", HeicOutput.PNG, heicOutputFor(probe))
        }
    }

    @Test
    fun `each format asks ffmpeg for its own codec`() {
        assertEquals(listOf("-vcodec", "bmp"), HeicOutput.BMP.arguments)
        assertEquals(listOf("-vcodec", "png", "-compression_level", "1"), HeicOutput.PNG.arguments)
    }

    @Test
    fun `the probe asks for the streams as well as their groups`() {
        val command = heicProbeCommand(java.nio.file.Path.of("a.heic"))

        assertEquals(true, "-show_streams" in command)
        assertEquals(true, "-show_stream_groups" in command)
    }
}
