package app.osprey.media

private const val BITS_PER_BYTE = 8

/**
 * Where a pixel of the result comes from: x of the source is [xFromX] times x plus [xFromY] times y, counted from the
 * far edge when [xFlipped]; y of the source likewise.
 */
private class Source(
    val xFromX: Int,
    val xFromY: Int,
    val xFlipped: Boolean,
    val yFromX: Int,
    val yFromY: Int,
    val yFlipped: Boolean,
)

// The EXIF definition of each orientation: which stored pixel shows at (x, y) of the upright picture.
private val SOURCES = mapOf(
    ExifOrientation.NORMAL to Source(1, 0, false, 0, 1, false),
    ExifOrientation.FLIP_HORIZONTAL to Source(-1, 0, true, 0, 1, false),
    ExifOrientation.ROTATE_180 to Source(-1, 0, true, 0, -1, true),
    ExifOrientation.FLIP_VERTICAL to Source(1, 0, false, 0, -1, true),
    ExifOrientation.TRANSPOSE to Source(0, 1, false, 1, 0, false),
    ExifOrientation.ROTATE_90 to Source(0, 1, false, -1, 0, true),
    ExifOrientation.TRANSVERSE to Source(0, -1, true, -1, 0, true),
    ExifOrientation.ROTATE_270 to Source(0, -1, true, 1, 0, false),
)

/** Copies one pixel of the scanlines [raw] of [from] into the scanlines [out] of [to]. */
private class PixelMover(
    private val raw: ByteArray,
    private val from: PngHeader,
    private val out: ByteArray,
    private val to: PngHeader,
) {
    fun move(sourceX: Int, sourceY: Int, x: Int, y: Int) {
        val fromRow = sourceY * (from.stride + 1) + 1
        val toRow = y * (to.stride + 1) + 1
        if (from.bitsPerPixel >= BITS_PER_BYTE) {
            val size = from.bytesPerPixel
            raw.copyInto(out, toRow + x * size, fromRow + sourceX * size, fromRow + (sourceX + 1) * size)
        } else {
            val bits = from.bitsPerPixel
            val packed = raw.u8(fromRow + sourceX * bits / BITS_PER_BYTE)
            val value = (packed shr shift(sourceX, bits)) and ((1 shl bits) - 1)
            val at = toRow + x * bits / BITS_PER_BYTE
            out[at] = (out.u8(at) or (value shl shift(x, bits))).toByte()
        }
    }

    // Pixels under a byte are packed from the high bits down.
    private fun shift(pixel: Int, bits: Int): Int = BITS_PER_BYTE - bits - pixel * bits % BITS_PER_BYTE
}

/** Moves whole pixels, whatever their depth, without looking at what they hold (#70). */
internal object PngPixels {

    /** The scanlines of [from] (pixels, no filtering) laid out for [to], every pixel moved as [orientation] says. */
    fun orient(raw: ByteArray, from: PngHeader, to: PngHeader, orientation: ExifOrientation): ByteArray {
        val out = ByteArray(to.rawSize.toInt())
        val mover = PixelMover(raw, from, out, to)
        val source = SOURCES.getValue(orientation)
        for (y in 0 until to.height) {
            for (x in 0 until to.width) {
                val sourceX = source.xFromX * x + source.xFromY * y + if (source.xFlipped) from.width - 1 else 0
                val sourceY = source.yFromX * x + source.yFromY * y + if (source.yFlipped) from.height - 1 else 0
                mover.move(sourceX, sourceY, x, y)
            }
        }
        return out
    }
}
