package app.osprey.desktop.media

import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridHeicRendererTest {

    private val photo = Path.of("photo.heic")
    private val whole = byteArrayOf(1, 2, 3)
    private val composed = byteArrayOf(9, 9)
    private val gridJson = """{"stream_groups": [{"type": "Tile Grid", "components": [{"width": 100, "height": 50,
        "subcomponents": [{"stream_index": 2, "tile_horizontal_offset": 0, "tile_vertical_offset": 0}]}]}]}"""

    private class Calls {
        val commands = mutableListOf<List<String>>()
        val probed = mutableListOf<Path>()
    }

    private fun renderer(
        calls: Calls,
        probe: String?,
        turns: Int = 0,
        run: (List<String>) -> ByteArray?,
    ) = GridHeicRenderer(
        probe = { path -> calls.probed.add(path); probe },
        run = { command -> calls.commands.add(command); run(command) },
        turns = { turns },
    )

    private fun isComposed(command: List<String>) = "-filter_complex" in command

    @Test
    fun `a photo with no tile grid is rendered by the plain command alone`() {
        val calls = Calls()

        val png = renderer(calls, probe = """{"stream_groups": []}""") { whole }.renderPng(photo)

        assertArrayEquals(whole, png)
        assertEquals(listOf(heicRenderCommand(photo)), calls.commands)
        assertEquals(listOf(photo), calls.probed)
    }

    @Test
    fun `a photo that cannot be probed is rendered by the plain command alone`() {
        val calls = Calls()

        renderer(calls, probe = null) { whole }.renderPng(photo)

        assertEquals(listOf(heicRenderCommand(photo)), calls.commands)
    }

    @Test
    fun `a tile grid is composed with its tiles and its turn, and nothing else is run`() {
        val calls = Calls()

        val png = renderer(calls, probe = gridJson, turns = 3) { if (isComposed(it)) composed else whole }.renderPng(photo)

        assertArrayEquals(composed, png)
        assertEquals(1, calls.commands.size)
        assertEquals(heicGridCommand(photo, parseHeicGrid(gridJson)!!, 3), calls.commands.single())
    }

    @Test
    fun `when the composed picture cannot be made the plain command is tried`() {
        val calls = Calls()

        val png = renderer(calls, probe = gridJson) { if (isComposed(it)) null else whole }.renderPng(photo)

        assertArrayEquals(whole, png)
        assertEquals(listOf(true, false), calls.commands.map(::isComposed))
    }

    @Test
    fun `nothing renders when every command fails`() {
        assertNull(renderer(Calls(), probe = gridJson) { null }.renderPng(photo))
    }
}
