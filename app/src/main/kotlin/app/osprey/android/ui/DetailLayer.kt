package app.osprey.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PhotoSize
import app.osprey.viewer.DetailTile
import app.osprey.viewer.ViewTransform
import app.osprey.viewer.detailTiles
import app.osprey.viewer.missingWithinBudget
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the zoomed detail may hold on top of the base picture: about 40 tiles of 512 px at 4 bytes a pixel. */
private const val DETAIL_BUDGET_BYTES = 40_000_000L

/** How long the view must hold still before its tiles are planned: a drag or pinch in motion decodes nothing. */
private const val DETAIL_SETTLE_MS = 150L

private const val HALF = 2f

/**
 * The full-resolution tiles of the part of the photo in view (#60), loaded in the background. A change of [transform]
 * cancels the pass in progress, waits for the view to settle, drops the tiles it no longer needs and decodes the
 * missing ones one at a time, tiles in view first, up to [DETAIL_BUDGET_BYTES]. A photo that cannot be read in
 * regions ends the pass at its first tile and keeps the base picture.
 */
@Composable
fun rememberDetailTiles(
    mediaMetadataReader: MediaMetadataReader,
    handle: FileHandle,
    revision: Int,
    transform: ViewTransform?,
    baseMaxSide: Int,
): Map<DetailTile, ImageBitmap> {
    var held by remember(handle, revision) { mutableStateOf<Map<DetailTile, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(handle, revision, transform) {
        if (transform == null) return@LaunchedEffect
        delay(DETAIL_SETTLE_MS)
        val plan = transform.detailTiles(baseMaxSide)
        held = held.filterKeys { it in plan }
        for (tile in missingWithinBudget(plan, held.keys, DETAIL_BUDGET_BYTES)) {
            val bitmap = withContext(Dispatchers.IO) {
                mediaMetadataReader.decodeRegion(handle, tile.region, tile.sampleSize)?.toImageBitmap()
            } ?: return@LaunchedEffect
            held = held + (tile to bitmap)
        }
    }
    return held
}

/** The tiles drawn where they belong on a photo laid out to fit this canvas; edges are rounded together, so no seam. */
@Composable
fun DetailTilesCanvas(photo: PhotoSize, tiles: Map<DetailTile, ImageBitmap>) {
    Canvas(Modifier.fillMaxSize()) {
        val fit = min(size.width / photo.width, size.height / photo.height)
        val originX = (size.width - photo.width * fit) / HALF
        val originY = (size.height - photo.height * fit) / HALF
        for ((tile, bitmap) in tiles) {
            val region = tile.region
            val left = (originX + region.left * fit).roundToInt()
            val top = (originY + region.top * fit).roundToInt()
            val right = (originX + (region.left + region.width) * fit).roundToInt()
            val bottom = (originY + (region.top + region.height) * fit).roundToInt()
            drawImage(
                bitmap,
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(right - left, bottom - top),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
