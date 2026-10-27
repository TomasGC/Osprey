package app.osprey.media

import kotlin.math.abs

/** A rectangle of whole pixels in a photo: [left] and [top] are its first pixel, [width] and [height] its size. */
data class PhotoRegion(val left: Int, val top: Int, val width: Int, val height: Int) {

    init {
        require(left >= 0 && top >= 0 && width > 0 && height > 0) { "invalid region $this" }
    }
}

/** Where this upright region lies in the stored pixels of a photo stored with [orientation]. */
fun PhotoRegion.toStored(orientation: ExifOrientation, uprightWidth: Int, uprightHeight: Int): PhotoRegion {
    val (x1, y1) = storedPixel(orientation, uprightWidth, uprightHeight, left, top)
    val (x2, y2) = storedPixel(orientation, uprightWidth, uprightHeight, left + width - 1, top + height - 1)
    return PhotoRegion(minOf(x1, x2), minOf(y1, y2), abs(x1 - x2) + 1, abs(y1 - y2) + 1)
}

/** Where the upright pixel ([x], [y]) is stored: the inverse of [oriented]'s destination. */
private fun storedPixel(orientation: ExifOrientation, uprightWidth: Int, uprightHeight: Int, x: Int, y: Int) =
    when (orientation) {
        ExifOrientation.NORMAL -> x to y
        ExifOrientation.FLIP_HORIZONTAL -> uprightWidth - 1 - x to y
        ExifOrientation.ROTATE_180 -> uprightWidth - 1 - x to uprightHeight - 1 - y
        ExifOrientation.FLIP_VERTICAL -> x to uprightHeight - 1 - y
        ExifOrientation.TRANSPOSE -> y to x
        ExifOrientation.ROTATE_90 -> y to uprightWidth - 1 - x
        ExifOrientation.TRANSVERSE -> uprightHeight - 1 - y to uprightWidth - 1 - x
        ExifOrientation.ROTATE_270 -> uprightHeight - 1 - y to x
    }

/** The pixels of this buffer inside [region]; the region must lie within the buffer. */
fun PixelBuffer.cropped(region: PhotoRegion): PixelBuffer {
    require(region.left + region.width <= width && region.top + region.height <= height) {
        "$region does not fit in $width x $height"
    }
    val result = IntArray(region.width * region.height)
    for (row in 0 until region.height) {
        val start = (region.top + row) * width + region.left
        pixels.copyInto(result, row * region.width, start, start + region.width)
    }
    return PixelBuffer(region.width, region.height, result)
}
