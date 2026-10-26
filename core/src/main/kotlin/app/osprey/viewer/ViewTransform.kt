package app.osprey.viewer

import kotlin.math.pow

/** Zoom 1 shows the whole photo inside the view. */
const val FIT_ZOOM = 1f
const val MAX_ZOOM = 8f

/** Where a double tap or double click zooms to, from fit. */
const val DOUBLE_TAP_ZOOM = 3f

/** The zoom factor of one notch of the mouse wheel. */
const val WHEEL_ZOOM_STEP = 1.25f

private const val QUARTER_TURNS_IN_FULL = 4
private const val ONE_TURN_LEFT = QUARTER_TURNS_IN_FULL - 1
private const val HALF = 2f

/**
 * Where a photo sits in a viewer (#51), for both platforms: zoom relative to "fit the view" (1 to [MAX_ZOOM]), pan as
 * the offset of the photo's centre from the view's centre, and the quarter turns the user has given it. Sizes and
 * positions are in view pixels, from the view's top-left corner. Pan is clamped so no empty border opens up, and an
 * axis on which the photo is smaller than the view stays centred.
 */
data class ViewTransform(
    val viewWidth: Float,
    val viewHeight: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val zoom: Float = FIT_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val quarterTurns: Int = 0,
) {
    init {
        require(minOf(viewWidth, viewHeight, imageWidth, imageHeight) > 0f) { "sizes must be positive" }
    }

    private val sideways get() = quarterTurns % 2 == 1
    private val turnedWidth get() = if (sideways) imageHeight else imageWidth
    private val turnedHeight get() = if (sideways) imageWidth else imageHeight

    /** View pixels per photo pixel at zoom 1: the photo, turned, just fits the view. */
    val fitScale: Float get() = minOf(viewWidth / turnedWidth, viewHeight / turnedHeight)

    /** View pixels per photo pixel as shown now. */
    val scale: Float get() = fitScale * zoom

    /** Zooms by [factor] keeping the photo point under ([focusX], [focusY]) where it is. */
    fun zoomAt(focusX: Float, focusY: Float, factor: Float): ViewTransform {
        val newZoom = (zoom * factor).coerceIn(FIT_ZOOM, MAX_ZOOM)
        val ratio = newZoom / zoom
        val fromCentreX = focusX - viewWidth / HALF
        val fromCentreY = focusY - viewHeight / HALF
        return copy(
            zoom = newZoom,
            panX = fromCentreX - (fromCentreX - panX) * ratio,
            panY = fromCentreY - (fromCentreY - panY) * ratio,
        ).clamped()
    }

    /** One wheel event: up ([scrollDelta] below zero) zooms in, down zooms out. */
    fun zoomByWheel(focusX: Float, focusY: Float, scrollDelta: Float): ViewTransform =
        zoomAt(focusX, focusY, WHEEL_ZOOM_STEP.pow(-scrollDelta))

    fun panBy(dx: Float, dy: Float): ViewTransform = copy(panX = panX + dx, panY = panY + dy).clamped()

    /** From fit, zooms in to [DOUBLE_TAP_ZOOM] at the point; from any zoom, goes back to fit. */
    fun doubleTap(focusX: Float, focusY: Float): ViewTransform =
        if (zoom > FIT_ZOOM) reset() else zoomAt(focusX, focusY, DOUBLE_TAP_ZOOM)

    fun rotateRight(): ViewTransform = turned(1)

    fun rotateLeft(): ViewTransform = turned(ONE_TURN_LEFT)

    /** Back to fit and centred; the turn stays. */
    fun reset(): ViewTransform = copy(zoom = FIT_ZOOM, panX = 0f, panY = 0f)

    fun resizedTo(width: Float, height: Float): ViewTransform = copy(viewWidth = width, viewHeight = height).clamped()

    private fun turned(quarters: Int): ViewTransform =
        reset().copy(quarterTurns = (quarterTurns + quarters) % QUARTER_TURNS_IN_FULL)

    private fun clamped(): ViewTransform {
        val limitX = ((turnedWidth * scale - viewWidth) / HALF).coerceAtLeast(0f)
        val limitY = ((turnedHeight * scale - viewHeight) / HALF).coerceAtLeast(0f)
        return copy(panX = panX.coerceIn(-limitX, limitX), panY = panY.coerceIn(-limitY, limitY))
    }
}
