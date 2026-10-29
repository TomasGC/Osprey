package app.osprey.media

import kotlin.math.abs

private const val FILTER_TYPES = 5
private const val NONE = 0
private const val SUB = 1
private const val UP = 2
private const val AVERAGE = 3

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

    /** Replaces every row of pixels by its filtered form, bottom up, each with the filter that spreads it least. */
    fun filter(raw: ByteArray, header: PngHeader) {
        val rowSize = header.stride + 1
        val candidates = Array(FILTER_TYPES) { ByteArray(header.stride) }
        for (y in header.height - 1 downTo 0) {
            val scores = LongArray(FILTER_TYPES) { filteredRow(raw, header, y, it, candidates[it]) }
            val best = scores.indices.minBy { scores[it] }
            raw[y * rowSize] = best.toByte()
            candidates[best].copyInto(raw, y * rowSize + 1)
        }
    }

    /** Fills [into] with row [y] filtered by [type]; returns how far its bytes spread from zero. */
    private fun filteredRow(raw: ByteArray, header: PngHeader, y: Int, type: Int, into: ByteArray): Long {
        var score = 0L
        var i = 1
        // A row has at least one byte: see unfilter.
        do {
            val value = (raw.u8(y * (header.stride + 1) + i) - predict(raw, header, y, i, type)).toByte()
            into[i - 1] = value
            score += abs(value.toInt())
        } while (++i <= header.stride)
        return score
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
