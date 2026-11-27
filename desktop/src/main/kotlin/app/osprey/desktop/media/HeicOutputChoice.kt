package app.osprey.desktop.media

import org.json.JSONException
import org.json.JSONObject

private const val TILE_GRID = "Tile Grid"

/** The pixel formats whose BMP holds the same pixels as their PNG: 8-bit 4:2:0, what phones write. */
private val BMP_SAFE_FORMATS = setOf("yuv420p", "yuvj420p")

/**
 * BMP when every stream of the picture in [probe] (ffprobe's JSON: the tile grid's streams, else the file's) is one
 * of [BMP_SAFE_FORMATS]; PNG for anything else, or when it cannot be told: a deeper source rounds differently in BMP.
 */
@Suppress("SwallowedException") // text that is not ffprobe's JSON says nothing: PNG
fun heicOutputFor(probe: String?): HeicOutput = try {
    val formats = probe?.let { pictureFormats(JSONObject(it)) }.orEmpty()
    if (formats.isNotEmpty() && formats.all { it in BMP_SAFE_FORMATS }) HeicOutput.BMP else HeicOutput.PNG
} catch (ignored: JSONException) {
    HeicOutput.PNG
}

private fun pictureFormats(root: JSONObject): List<String> {
    val groups = root.optJSONArray("stream_groups")
    val grid = (0 until (groups?.length() ?: 0)).map { groups!!.getJSONObject(it) }
        .firstOrNull { it.optString("type") == TILE_GRID }
    val streams = (grid ?: root).optJSONArray("streams") ?: return emptyList()
    return (0 until streams.length()).map { streams.getJSONObject(it).optString("pix_fmt") }
}
