package app.osprey.desktop.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The grid of a phone HEIC as `ffprobe -show_stream_groups -of json` reports it. */
class HeicGridTest {

    private val tiles = """, "subcomponents": [
        {"stream_index": 4, "tile_horizontal_offset": 0, "tile_vertical_offset": 0},
        {"stream_index": 7, "tile_horizontal_offset": 512, "tile_vertical_offset": 0}]"""

    private fun group(type: String = "Tile Grid", subcomponents: String = tiles) = """
        {"stream_groups": [{"index": 0, "id": "0x31", "nb_streams": 2, "type": "$type",
          "components": [{"nb_tiles": 2, "coded_width": 1024, "coded_height": 512, "horizontal_offset": 3,
            "vertical_offset": 5, "width": 1000, "height": 500 $subcomponents}]}]}
    """

    @Test
    fun `a tile grid gives its picture size, its crop and its tiles in order`() {
        val grid = parseHeicGrid(group())!!

        assertEquals(1000, grid.width)
        assertEquals(500, grid.height)
        assertEquals(3, grid.cropX)
        assertEquals(5, grid.cropY)
        assertEquals(listOf(Triple(4, 0, 0), Triple(7, 512, 0)), grid.tiles.map { Triple(it.streamIndex, it.x, it.y) })
    }

    @Test
    fun `a file with no stream group has no grid`() {
        assertNull(parseHeicGrid("""{"stream_groups": []}"""))
        assertNull(parseHeicGrid("{}"))
    }

    @Test
    fun `a group that is not a tile grid is not one`() {
        assertNull(parseHeicGrid(group(type = "IAMF Audio Element")))
    }

    @Test
    fun `a grid with no tiles has no grid`() {
        assertNull(parseHeicGrid(group(subcomponents = """, "subcomponents": []""")))
        assertNull(parseHeicGrid(group(subcomponents = "")))
    }

    @Test
    fun `a grid without a picture size has no grid`() {
        assertNull(parseHeicGrid(group().replace("\"width\": 1000, ", "")))
    }

    @Test
    fun `text that is not JSON has no grid`() {
        assertNull(parseHeicGrid("not json"))
        assertNull(parseHeicGrid(""))
    }

    @Test
    fun `the first tile grid wins when several groups are listed`() {
        val two = """{"stream_groups": [
            {"type": "IAMF Audio Element", "components": []},
            {"type": "Tile Grid", "components": [{"width": 1000, "height": 500, "subcomponents": [
              {"stream_index": 0, "tile_horizontal_offset": 0, "tile_vertical_offset": 0}]}]},
            {"type": "Tile Grid", "components": [{"width": 1, "height": 1, "subcomponents": [
              {"stream_index": 0, "tile_horizontal_offset": 0, "tile_vertical_offset": 0}]}]}]}"""

        assertEquals(1000, parseHeicGrid(two)!!.width)
    }
}
