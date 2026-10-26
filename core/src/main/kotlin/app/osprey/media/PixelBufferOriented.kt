package app.osprey.media

/** This buffer as it looks upright, given the [orientation] stored with the photo. Normal returns the same buffer. */
fun PixelBuffer.oriented(orientation: ExifOrientation): PixelBuffer {
    if (orientation == ExifOrientation.NORMAL) return this
    val newWidth = if (orientation.swapsDimensions) height else width
    val newHeight = if (orientation.swapsDimensions) width else height
    val result = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val (destX, destY) = destination(orientation, x, y)
            result[destY * newWidth + destX] = pixels[y * width + x]
        }
    }
    return PixelBuffer(newWidth, newHeight, result)
}

/** Where the pixel at ([x], [y]) lands; [ExifOrientation.NORMAL] never gets here, so the last case is the else. */
private fun PixelBuffer.destination(orientation: ExifOrientation, x: Int, y: Int): Pair<Int, Int> =
    when (orientation) {
        ExifOrientation.FLIP_HORIZONTAL -> width - 1 - x to y
        ExifOrientation.ROTATE_180 -> width - 1 - x to height - 1 - y
        ExifOrientation.FLIP_VERTICAL -> x to height - 1 - y
        ExifOrientation.TRANSPOSE -> y to x
        ExifOrientation.ROTATE_90 -> height - 1 - y to x
        ExifOrientation.TRANSVERSE -> height - 1 - y to width - 1 - x
        else -> y to width - 1 - x // ROTATE_270
    }
