package app.osprey.viewer

import app.osprey.media.PhotoRegion
import app.osprey.media.subsampleStepFor
import kotlin.math.ceil
import kotlin.math.floor

/** Pixels a side of a detail tile as decoded: a tile covers this many pixels times its sample size of the photo. */
const val TILE_SIZE = 512

private const val HALF = 2f

/** One piece of the photo to decode at [sampleSize]: [region] is in full-resolution pixels of the upright photo. */
data class DetailTile(val region: PhotoRegion, val sampleSize: Int)

/**
 * The tiles to draw over the base picture so the part of the photo in view shows more detail than the base (#60), or
 * none when the base, decoded for [baseMaxSide], already holds as much. The transform's photo size is the full
 * photo's, not the base picture's. Tiles are aligned on a grid, clipped to the photo, and include half a tile of
 * margin so a short drag stays sharp. The tiles in view come first, then the margin ones, each group sorted from the
 * view's centre outwards: a loader that stops at its memory budget only ever drops margin.
 */
fun ViewTransform.detailTiles(baseMaxSide: Int): List<DetailTile> {
    val photoWidth = imageWidth.toInt()
    val photoHeight = imageHeight.toInt()
    val sampleSize = sampleSizeFor(scale)
    if (sampleSize >= subsampleStepFor(photoWidth, photoHeight, baseMaxSide)) return emptyList()
    val span = TILE_SIZE * sampleSize
    val visible = visibleUpright()
    val margin = span / HALF
    val columns = tileRange(visible.left - margin, visible.right + margin, photoWidth, span)
    val rows = tileRange(visible.top - margin, visible.bottom + margin, photoHeight, span)
    val centreX = (visible.left + visible.right) / HALF
    val centreY = (visible.top + visible.bottom) / HALF
    val inViewColumns = tileRange(visible.left, visible.right, photoWidth, span)
    val inViewRows = tileRange(visible.top, visible.bottom, photoHeight, span)
    return rows
        .flatMap { row ->
            columns.map { column ->
                val left = column * span
                val top = row * span
                PhotoRegion(left, top, minOf(span, photoWidth - left), minOf(span, photoHeight - top))
            }
        }
        .sortedWith(
            compareBy(
                { !(it.left / span in inViewColumns && it.top / span in inViewRows) },
                { (it.left + it.width / HALF - centreX).squared() + (it.top + it.height / HALF - centreY).squared() },
            ),
        )
        .map { DetailTile(it, sampleSize) }
}

private fun Float.squared() = this * this

/** The largest power of two that is at most one photo pixel per view pixel; 1 when zoomed past real pixels. */
private fun sampleSizeFor(scale: Float): Int {
    var sampleSize = 1
    while (sampleSize * 2 * scale <= 1f) sampleSize *= 2
    return sampleSize
}

/** The tile indices covering [from] to [to], limited to a photo [size] pixels long. */
private fun tileRange(from: Float, to: Float, size: Int, span: Int): IntRange =
    floor(from.coerceAtLeast(0f) / span).toInt()..ceil(to.coerceAtMost(size.toFloat()) / span).toInt() - 1

private class VisibleRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The part of the photo in view, in pixels of the upright photo: the view turn is undone on its two corners. */
private fun ViewTransform.visibleUpright(): VisibleRect {
    val sideways = quarterTurns % 2 == 1
    val turnedWidth = if (sideways) imageHeight else imageWidth
    val turnedHeight = if (sideways) imageWidth else imageHeight
    val centreX = viewWidth / HALF + panX
    val centreY = viewHeight / HALF + panY
    val (x1, y1) = unturned(
        (turnedWidth / HALF - centreX / scale).coerceIn(0f, turnedWidth),
        (turnedHeight / HALF - centreY / scale).coerceIn(0f, turnedHeight),
    )
    val (x2, y2) = unturned(
        (turnedWidth / HALF + (viewWidth - centreX) / scale).coerceIn(0f, turnedWidth),
        (turnedHeight / HALF + (viewHeight - centreY) / scale).coerceIn(0f, turnedHeight),
    )
    return VisibleRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
}

/** The upright photo point shown at the turned photo point ([x], [y]). */
private fun ViewTransform.unturned(x: Float, y: Float): Pair<Float, Float> = when (quarterTurns) {
    0 -> x to y
    1 -> y to imageHeight - x
    2 -> imageWidth - x to imageHeight - y
    else -> imageWidth - y to x
}
