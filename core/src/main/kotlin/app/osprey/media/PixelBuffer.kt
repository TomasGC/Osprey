package app.osprey.media

/**
 * A decoded image, row-major, one ARGB int per pixel (0xAARRGGBB) — deliberately independent of
 * `BufferedImage`/`Bitmap` so [MediaMetadataReader] stays usable from `:core`, which has neither.
 */
class PixelBuffer(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) {
            "pixels.size (${pixels.size}) must equal width * height (${width * height})"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PixelBuffer && width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = (width * 31 + height) * 31 + pixels.contentHashCode()

    override fun toString(): String = "PixelBuffer(width=$width, height=$height)"
}
