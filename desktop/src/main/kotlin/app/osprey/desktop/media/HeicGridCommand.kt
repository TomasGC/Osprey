package app.osprey.desktop.media

import java.nio.file.Path

private const val TURN_CLOCKWISE = "transpose=1"
private const val TURN_COUNTERCLOCKWISE = "transpose=2"
private const val PNG_COMPRESSION_LEVEL = "1"

/** The filter that shows a picture upright for an `irot` of 0, 1, 2 and 3 anticlockwise quarter turns. */
private val TURN_FILTERS = listOf("", ",$TURN_COUNTERCLOCKWISE", ",$TURN_CLOCKWISE,$TURN_CLOCKWISE", ",$TURN_CLOCKWISE")

/**
 * The ffmpeg call that composes the tiles of [grid] into one picture, turns it by [quarterTurns] (the `irot` of the
 * photo, anticlockwise) and writes a PNG on stdout. An ffmpeg before 8.1 decodes only the first tile of a grid, and
 * shows no rotation on a composed one, so both are done here: `xstack` places the tiles where the grid says, `crop`
 * cuts the coded picture to the picture's size, `transpose` turns it.
 */
fun heicGridCommand(path: Path, grid: HeicGrid, quarterTurns: Int): List<String> {
    val inputs = grid.tiles.joinToString("") { "[0:${it.streamIndex}]" }
    val layout = grid.tiles.joinToString("|") { "${it.x}_${it.y}" }
    val crop = "crop=${grid.width}:${grid.height}:${grid.cropX}:${grid.cropY}"
    val turn = TURN_FILTERS[quarterTurns.mod(TURN_FILTERS.size)]
    val graph = "${inputs}xstack=inputs=${grid.tiles.size}:layout=$layout,$crop$turn[v]"
    return listOf(
        "ffmpeg", "-nostdin", "-v", "error", "-i", path.toString(), "-filter_complex", graph, "-map", "[v]",
        "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-compression_level", PNG_COMPRESSION_LEVEL, "-",
    )
}
