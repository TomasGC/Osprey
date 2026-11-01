package app.osprey.desktop.media

import org.json.JSONException
import org.json.JSONObject

private const val TILE_GRID = "Tile Grid"

/** One tile of a HEIC's grid: the ffmpeg stream that holds it and where it sits in the coded picture. */
class HeicTile(val streamIndex: Int, val x: Int, val y: Int)

/** The tile grid of a HEIC: the size of the picture it shows, where it starts in the coded picture, its tiles. */
class HeicGrid(val width: Int, val height: Int, val cropX: Int, val cropY: Int, val tiles: List<HeicTile>)

/** The first tile grid in [json] (`ffprobe -show_stream_groups -of json`), or null when the file has none. */
@Suppress("SwallowedException") // text that is not ffprobe's JSON means no grid, and the plain command is tried
fun parseHeicGrid(json: String): HeicGrid? = try {
    val groups = JSONObject(json).optJSONArray("stream_groups")
    groups?.let { list -> (0 until list.length()).firstNotNullOfOrNull { gridOf(list.optJSONObject(it)) } }
} catch (ignored: JSONException) {
    null
}

private fun gridOf(group: JSONObject?): HeicGrid? {
    if (group?.optString("type") != TILE_GRID) return null
    val component = group.optJSONArray("components")?.optJSONObject(0) ?: return null
    val width = component.optInt("width")
    val height = component.optInt("height")
    val list = component.optJSONArray("subcomponents")
    val tiles = (0 until (list?.length() ?: 0)).map { index ->
        val tile = list!!.getJSONObject(index)
        val x = tile.optInt("tile_horizontal_offset")
        HeicTile(tile.optInt("stream_index"), x, tile.optInt("tile_vertical_offset"))
    }
    if (width <= 0 || height <= 0 || tiles.isEmpty()) return null
    return HeicGrid(width, height, component.optInt("horizontal_offset"), component.optInt("vertical_offset"), tiles)
}
