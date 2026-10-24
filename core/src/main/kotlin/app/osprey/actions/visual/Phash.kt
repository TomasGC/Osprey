package app.osprey.actions.visual

import app.osprey.media.PixelBuffer
import kotlin.math.PI
import kotlin.math.cos

private const val HASH_SIZE = 8
private const val RESIZE_FACTOR = 4
private const val IMG_SIZE = HASH_SIZE * RESIZE_FACTOR

private const val RED_LUMA_WEIGHT = 0.299
private const val GREEN_LUMA_WEIGHT = 0.587
private const val BLUE_LUMA_WEIGHT = 0.114

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE_MASK = 0xFF

// The DCT-II sample point for index x is (x + 0.5), not x -- standard DCT-II definition.
private const val DCT_SAMPLE_OFFSET = 0.5

/**
 * Perceptual hash of [pixels], as a 64-bit value: downsample to a 32x32 grayscale grid, apply a
 * 2D DCT, median-threshold the low-frequency 8x8 corner into 64 bits. Hand-rolled — no JVM
 * equivalent of Python's `imagehash.phash` was worth a dependency for ~80 lines of DCT math —
 * against the platform-agnostic [PixelBuffer] (#2), so `:core` never needs `BufferedImage` or
 * `Bitmap`.
 *
 * Not bit-for-bit identical to `imagehash.phash`'s output — a different DCT implementation
 * produces a different but equally valid hash space. What must hold, and is what every caller
 * actually relies on, is the *behavior*: near-identical images hash close together (Hamming
 * distance via [Long.countOneBits] on xor, see [VisualDupePlanner]), structurally different
 * images hash far apart.
 */
fun phash(pixels: PixelBuffer): Long {
    val grayscale = toGrayscale(pixels)
    val resized = resizeGrayscale(grayscale, pixels.width, pixels.height, IMG_SIZE, IMG_SIZE)
    val dct = dct2d(resized, IMG_SIZE)
    val lowFreq = lowFrequencyCorner(dct)
    val median = median(lowFreq)

    var hash = 0L
    for (i in lowFreq.indices) {
        if (lowFreq[i] > median) hash = hash or (1L shl i)
    }
    return hash
}

private fun toGrayscale(pixels: PixelBuffer): DoubleArray =
    DoubleArray(pixels.pixels.size) { i ->
        val argb = pixels.pixels[i]
        val red = (argb shr RED_SHIFT) and BYTE_MASK
        val green = (argb shr GREEN_SHIFT) and BYTE_MASK
        val blue = argb and BYTE_MASK
        red * RED_LUMA_WEIGHT + green * GREEN_LUMA_WEIGHT + blue * BLUE_LUMA_WEIGHT
    }

/**
 * Area-average (box filter) resize — smooths out recompression noise, same reason phash is
 * robust to it in the first place; a naive nearest-neighbor resize would undermine that.
 */
private fun resizeGrayscale(
    source: DoubleArray,
    srcWidth: Int,
    srcHeight: Int,
    destWidth: Int,
    destHeight: Int,
): DoubleArray {
    val dest = DoubleArray(destWidth * destHeight)
    for (destY in 0 until destHeight) {
        val srcY0 = destY * srcHeight / destHeight
        val srcY1 = maxOf(srcY0 + 1, (destY + 1) * srcHeight / destHeight)
        for (destX in 0 until destWidth) {
            val srcX0 = destX * srcWidth / destWidth
            val srcX1 = maxOf(srcX0 + 1, (destX + 1) * srcWidth / destWidth)
            dest[destY * destWidth + destX] = averageRegion(source, srcWidth, srcX0 until srcX1, srcY0 until srcY1)
        }
    }
    return dest
}

private fun averageRegion(source: DoubleArray, srcWidth: Int, xRange: IntRange, yRange: IntRange): Double {
    var sum = 0.0
    var count = 0
    yRange.forEach { y ->
        xRange.forEach { x ->
            sum += source[y * srcWidth + x]
            count++
        }
    }
    return sum / count
}

/** Separable 2D DCT-II (rows, then columns), unnormalized — only relative ordering within the
 * low-frequency corner matters for the median threshold, so normalization is unnecessary. */
private fun dct2d(data: DoubleArray, n: Int): DoubleArray {
    val afterRows = DoubleArray(n * n)
    for (row in 0 until n) {
        val transformed = dct1d(DoubleArray(n) { col -> data[row * n + col] })
        for (col in 0 until n) afterRows[row * n + col] = transformed[col]
    }
    val afterCols = DoubleArray(n * n)
    for (col in 0 until n) {
        val transformed = dct1d(DoubleArray(n) { row -> afterRows[row * n + col] })
        for (row in 0 until n) afterCols[row * n + col] = transformed[row]
    }
    return afterCols
}

private fun dct1d(f: DoubleArray): DoubleArray {
    val n = f.size
    return DoubleArray(n) { u ->
        var sum = 0.0
        for (x in 0 until n) {
            sum += f[x] * cos(PI / n * (x + DCT_SAMPLE_OFFSET) * u)
        }
        sum
    }
}

private fun lowFrequencyCorner(dct: DoubleArray): DoubleArray =
    DoubleArray(HASH_SIZE * HASH_SIZE) { i -> dct[(i / HASH_SIZE) * IMG_SIZE + (i % HASH_SIZE)] }

private fun median(values: DoubleArray): Double {
    val sorted = values.sortedArray()
    val mid = sorted.size / 2
    return (sorted[mid - 1] + sorted[mid]) / 2.0
}
