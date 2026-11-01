package app.osprey.desktop.media

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeicGridCommandTest {

    private val grid = HeicGrid(
        width = 1000, height = 500, cropX = 0, cropY = 0,
        tiles = listOf(HeicTile(4, 0, 0), HeicTile(7, 512, 0), HeicTile(9, 0, 256)),
    )

    private fun graph(turns: Int = 0, grid: HeicGrid = this.grid): String {
        val command = heicGridCommand(Path.of("my photos", "IMG 1.heic"), grid, turns)
        return command[command.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `the command reads the photo, maps the composed picture and writes one PNG on stdout`() {
        val command = heicGridCommand(Path.of("my photos", "IMG 1.heic"), grid, 0)

        assertEquals("ffmpeg", command.first())
        assertEquals(Path.of("my photos", "IMG 1.heic").toString(), command[command.indexOf("-i") + 1])
        assertEquals("[v]", command[command.indexOf("-map") + 1])
        assertEquals("1", command[command.indexOf("-frames:v") + 1])
        assertEquals("png", command[command.indexOf("-vcodec") + 1])
        assertEquals("image2pipe", command[command.indexOf("-f") + 1])
        assertEquals("1", command[command.indexOf("-compression_level") + 1])
        assertTrue("-nostdin" in command)
        assertEquals("-", command.last())
    }

    @Test
    fun `the tiles are placed where the grid says, then cropped to the picture`() {
        assertEquals("[0:4][0:7][0:9]xstack=inputs=3:layout=0_0|512_0|0_256,crop=1000:500:0:0[v]", graph())
    }

    @Test
    fun `the crop starts at the grid's offset`() {
        assertTrue(graph(grid = HeicGrid(1000, 500, 3, 5, grid.tiles)).contains(",crop=1000:500:3:5"))
    }

    @Test
    fun `a quarter turn of irot is a transpose that turns the picture the same way`() {
        assertTrue(graph(1).endsWith("crop=1000:500:0:0,transpose=2[v]"))
        assertTrue(graph(2).endsWith("crop=1000:500:0:0,transpose=1,transpose=1[v]"))
        assertTrue(graph(3).endsWith("crop=1000:500:0:0,transpose=1[v]"))
        assertTrue(graph(0).endsWith("crop=1000:500:0:0[v]"))
    }

    @Test
    fun `turns count modulo four`() {
        assertEquals(graph(1), graph(5))
        assertEquals(graph(3), graph(-1))
    }
}
