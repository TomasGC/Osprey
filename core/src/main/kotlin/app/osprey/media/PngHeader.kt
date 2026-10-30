package app.osprey.media

private const val HEADER_SIZE = 13
private const val WIDTH_AT = 0
private const val HEIGHT_AT = 4
private const val DEPTH_AT = 8
private const val COLOR_AT = 9
private const val COMPRESSION_AT = 10
private const val FILTER_AT = 11
private const val INTERLACE_AT = 12
private const val BITS_PER_BYTE = 8
private const val FILTER_BYTE = 1
private const val COLOR_PALETTE = 3

// PNG specification, table of colour types: the bit depths each allows, and its samples per pixel.
@Suppress("MagicNumber")
private val DEPTHS = mapOf(
    0 to setOf(1, 2, 4, 8, 16), 2 to setOf(8, 16), 3 to setOf(1, 2, 4, 8), 4 to setOf(8, 16), 6 to setOf(8, 16),
)

@Suppress("MagicNumber")
private val CHANNELS = mapOf(0 to 1, 2 to 3, 3 to 1, 4 to 2, 6 to 4)

/** What the IHDR chunk says about the picture: its size and how each pixel is stored. */
internal class PngHeader(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int) {

    val bitsPerPixel: Int = bitDepth * CHANNELS.getValue(colorType)

    /** Bytes one whole pixel takes; a pixel under a byte counts as one. */
    val bytesPerPixel: Int = maxOf(1, bitsPerPixel / BITS_PER_BYTE)

    val isPalette: Boolean = colorType == COLOR_PALETTE

    private val rowBytes: Long = (width.toLong() * bitsPerPixel + BITS_PER_BYTE - 1) / BITS_PER_BYTE

    /** Bytes of one row of pixels, without its filter byte; only meaningful once [rawSize] has been limited. */
    val stride: Int = rowBytes.toInt()

    /** Bytes of all the scanlines, each with its filter byte: what one copy of the picture takes in memory. */
    val rawSize: Long = height.toLong() * (rowBytes + FILTER_BYTE)

    fun swapped(): PngHeader = PngHeader(height, width, bitDepth, colorType)

    /** [original] header data with this header's size. */
    fun toBytes(original: ByteArray): ByteArray = original.copyOf().also {
        it.putU32(WIDTH_AT, width)
        it.putU32(HEIGHT_AT, height)
    }

    companion object {
        fun parse(data: ByteArray): PngHeader {
            if (data.size != HEADER_SIZE) refuse(PngTurn.DAMAGED)
            val width = data.u32(WIDTH_AT, true)
            val height = data.u32(HEIGHT_AT, true)
            val depth = data.u8(DEPTH_AT)
            val colorType = data.u8(COLOR_AT)
            val valid = width > 0 && height > 0 && DEPTHS[colorType]?.contains(depth) == true &&
                data.u8(COMPRESSION_AT) == 0 && data.u8(FILTER_AT) == 0 && data.u8(INTERLACE_AT) <= 1
            if (!valid) refuse(PngTurn.DAMAGED)
            if (data.u8(INTERLACE_AT) == 1) refuse(PngTurn.INTERLACED)
            return PngHeader(width, height, depth, colorType)
        }
    }
}
