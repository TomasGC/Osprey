package app.osprey.media

import kotlin.math.abs

private const val FILTER_TYPES = 5
private const val NONE = 0
private const val SUB = 1
private const val UP = 2
private const val AVERAGE = 3
private const val BITS_PER_BYTE = 8

/**
 * The five PNG row filters (#70). Rows are laid out as the file has them: a filter byte, then [PngHeader.stride]
 * bytes. Both directions work in place, so a picture is held once, not twice.
 */
internal object PngScanlines {

    /** Replaces every filtered row by its pixels, top down: a row is rebuilt from the rows above it. */
    fun unfilter(raw: ByteArray, header: PngHeader) {
        val rowSize = header.stride + 1
        for (y in 0 until header.height) {
            val type = raw.u8(y * rowSize)
            if (type >= FILTER_TYPES) refuse(PngTurn.DAMAGED)
            var i = 1
            // A row has at least one byte, so this loop checks at its end: a range loop would carry a branch
            // that no test can take.
            do {
                val at = y * rowSize + i
                raw[at] = (raw.u8(at) + predict(raw, header, y, i, type)).toByte()
            } while (++i <= header.stride)
        }
    }

    /**
     * Replaces every row of pixels by its filtered form, bottom up so a row still sees the unfiltered one above it.
     * Up for pixels of a byte or more, none for palette images and smaller pixels (#70): measured on real 12 MP
     * photos and a screenshot, Up gave the smallest file every time at a sixth of the cost of choosing per row.
     */
    fun filter(raw: ByteArray, header: PngHeader) {
        val rowSize = header.stride + 1
        val type = if (header.isPalette || header.bitsPerPixel < BITS_PER_BYTE) NONE else UP
        for (y in header.height - 1 downTo 0) {
            raw[y * rowSize] = type.toByte()
            if (type == UP && y > 0) subtractRowAbove(raw, y * rowSize, rowSize)
        }
    }

    private fun subtractRowAbove(raw: ByteArray, row: Int, rowSize: Int) {
        var i = 1
        // A row has at least one byte, so this loop checks at its end: a range loop would carry a branch
        // that no test can take.
        do {
            raw[row + i] = (raw.u8(row + i) - raw.u8(row - rowSize + i)).toByte()
        } while (++i < rowSize)
    }

    /** What [type] predicts for byte [i] of row [y], from the left, upper and upper-left bytes as pixels. */
    private fun predict(raw: ByteArray, header: PngHeader, y: Int, i: Int, type: Int): Int {
        if (type == NONE) return 0
        val rowSize = header.stride + 1
        val hasLeft = i > header.bytesPerPixel
        val hasUp = y > 0
        val left = if (hasLeft) raw.u8(y * rowSize + i - header.bytesPerPixel) else 0
        val up = if (hasUp) raw.u8((y - 1) * rowSize + i) else 0
        val upLeft = if (hasLeft && hasUp) raw.u8((y - 1) * rowSize + i - header.bytesPerPixel) else 0
        return when (type) {
            SUB -> left
            UP -> up
            AVERAGE -> (left + up) / 2
            else -> paeth(left, up, upLeft)
        }
    }

    private fun paeth(left: Int, up: Int, upLeft: Int): Int {
        val estimate = left + up - upLeft
        val fromLeft = abs(estimate - left)
        val fromUp = abs(estimate - up)
        val fromUpLeft = abs(estimate - upLeft)
        return when {
            fromLeft <= fromUp && fromLeft <= fromUpLeft -> left
            fromUp <= fromUpLeft -> up
            else -> upLeft
        }
    }
}
