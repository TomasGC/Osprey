package app.osprey.media

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs

internal const val GRAY = 0
internal const val RGB = 2
internal const val PALETTE = 3
internal const val GRAY_ALPHA = 4
internal const val RGBA = 6

private const val BYTE_MASK = 0xFF
private const val SIGNATURE_SIZE = 8
private const val CHUNK_OVERHEAD = 12
private const val DEFAULT_IDAT_SIZE = 16
private const val BITS_PER_BYTE = 8
private const val FILTER_TYPES = 5

internal val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

internal fun channelsOf(colorType: Int): Int = when (colorType) {
    GRAY, PALETTE -> 1
    GRAY_ALPHA -> 2
    RGB -> 3
    else -> 4
}

/** An image as the tests see it: one Int per sample, a row after the other. */
internal class Samples(val width: Int, val height: Int, val depth: Int, val colorType: Int, val values: IntArray) {
    val channels: Int get() = channelsOf(colorType)

    fun at(x: Int, y: Int, channel: Int): Int = values[(y * width + x) * channels + channel]

    fun sameAs(other: Samples): Boolean = width == other.width && height == other.height && depth == other.depth &&
        colorType == other.colorType && values.contentEquals(other.values)

    /** The picture [orientation] shows upright, every pixel looked up by the EXIF definition of the orientation. */
    fun oriented(orientation: ExifOrientation): Samples {
        val outWidth = if (orientation.swapsDimensions) height else width
        val outHeight = if (orientation.swapsDimensions) width else height
        val out = IntArray(outWidth * outHeight * channels)
        for (y in 0 until outHeight) {
            for (x in 0 until outWidth) {
                val (sourceX, sourceY) = source(orientation, x, y)
                for (c in 0 until channels) out[(y * outWidth + x) * channels + c] = at(sourceX, sourceY, c)
            }
        }
        return Samples(outWidth, outHeight, depth, colorType, out)
    }

    private fun source(orientation: ExifOrientation, x: Int, y: Int): Pair<Int, Int> = when (orientation) {
        ExifOrientation.NORMAL -> x to y
        ExifOrientation.FLIP_HORIZONTAL -> width - 1 - x to y
        ExifOrientation.ROTATE_180 -> width - 1 - x to height - 1 - y
        ExifOrientation.FLIP_VERTICAL -> x to height - 1 - y
        ExifOrientation.TRANSPOSE -> y to x
        ExifOrientation.ROTATE_90 -> y to height - 1 - x
        ExifOrientation.TRANSVERSE -> width - 1 - y to height - 1 - x
        ExifOrientation.ROTATE_270 -> width - 1 - y to x
    }
}

internal fun randomSamples(width: Int, height: Int, depth: Int, colorType: Int, seed: Long = 1): Samples {
    val random = Random(seed)
    val values = IntArray(width * height * channelsOf(colorType)) { random.nextInt(1 shl depth) }
    return Samples(width, height, depth, colorType, values)
}

internal fun int32(value: Int): ByteArray =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

internal fun readInt(bytes: ByteArray, at: Int): Int = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (bytes[at + i].toInt() and BYTE_MASK) }

internal fun chunk(type: String, data: ByteArray = ByteArray(0)): ByteArray {
    val body = type.toByteArray(Charsets.US_ASCII) + data
    return int32(data.size) + body + int32(CRC32().apply { update(body) }.value.toInt())
}

internal fun ihdrData(width: Int, height: Int, depth: Int, colorType: Int, interlace: Int = 0): ByteArray =
    int32(width) + int32(height) + byteArrayOf(depth.toByte(), colorType.toByte(), 0, 0, interlace.toByte())

internal fun plte(depth: Int): ByteArray = chunk("PLTE", ByteArray(3 * (1 shl depth)) { (it * 37 + 11).toByte() })

internal fun exifChunk(orientation: Int?, bigEndian: Boolean = false): ByteArray =
    chunk("eXIf", tiff(bigEndian, orientation, TYPE_SHORT, 1, null))

/** A PNG file from raw chunks, for the damaged ones. */
internal fun rawPng(vararg chunks: ByteArray): ByteArray = chunks.fold(PNG_SIGNATURE) { acc, part -> acc + part }

private fun packRow(samples: Samples, y: Int): ByteArray {
    val out = ByteArray((samples.width * samples.depth * samples.channels + BITS_PER_BYTE - 1) / BITS_PER_BYTE)
    var bit = 0
    for (x in 0 until samples.width) {
        for (c in 0 until samples.channels) {
            val value = samples.at(x, y, c)
            for (b in samples.depth - 1 downTo 0) {
                if ((value shr b) and 1 == 1) out[bit / BITS_PER_BYTE] = (out[bit / BITS_PER_BYTE].toInt() or (0x80 shr (bit % BITS_PER_BYTE))).toByte()
                bit++
            }
        }
    }
    return out
}

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = abs(p - a)
    val pb = abs(p - b)
    val pc = abs(p - c)
    return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
}

private fun predictor(type: Int, a: Int, b: Int, c: Int): Int = when (type) {
    0 -> 0
    1 -> a
    2 -> b
    3 -> (a + b) / 2
    else -> paeth(a, b, c)
}

private fun at(bytes: ByteArray, i: Int): Int = if (i >= 0) bytes[i].toInt() and BYTE_MASK else 0

/** The scanlines of [samples], row y filtered with type [filters] of y. */
internal fun scanlines(samples: Samples, filters: (Int) -> Int): ByteArray {
    val bytesPerPixel = maxOf(1, samples.depth * samples.channels / BITS_PER_BYTE)
    val out = ByteArrayOutputStream()
    var previous = ByteArray((samples.width * samples.depth * samples.channels + BITS_PER_BYTE - 1) / BITS_PER_BYTE)
    for (y in 0 until samples.height) {
        val row = packRow(samples, y)
        val type = filters(y)
        out.write(type)
        out.write(ByteArray(row.size) { i ->
            (at(row, i) - predictor(type, at(row, i - bytesPerPixel), at(previous, i), at(previous, i - bytesPerPixel))).toByte()
        })
        previous = row
    }
    return out.toByteArray()
}

internal fun deflate(data: ByteArray): ByteArray =
    ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(data) } }.toByteArray()

/**
 * A PNG of [samples]: a palette chunk when needed, the [before] chunks, the image data cut into IDAT chunks of
 * [idatSize] bytes with row y filtered by [filters] (every type in turn by default), the [after] chunks, IEND.
 */
internal fun pngOf(
    samples: Samples,
    before: List<ByteArray> = emptyList(),
    after: List<ByteArray> = emptyList(),
    filters: (Int) -> Int = { it % FILTER_TYPES },
    idatSize: Int = DEFAULT_IDAT_SIZE,
    interlace: Int = 0,
): ByteArray {
    val palette = if (samples.colorType == PALETTE) listOf(plte(samples.depth)) else emptyList()
    val idats = deflate(scanlines(samples, filters)).asList().chunked(idatSize).map { chunk("IDAT", it.toByteArray()) }
    val ihdr = chunk("IHDR", ihdrData(samples.width, samples.height, samples.depth, samples.colorType, interlace))
    return rawPng(*(listOf(ihdr) + palette + before + idats + after + chunk("IEND")).toTypedArray())
}

internal class ParsedChunk(val type: String, val data: ByteArray, val bytes: ByteArray, val crcOk: Boolean)

/** The chunks of a PNG the test trusts to be well formed; each says whether its CRC is right. */
internal fun chunksOf(png: ByteArray): List<ParsedChunk> {
    val chunks = mutableListOf<ParsedChunk>()
    var at = SIGNATURE_SIZE
    while (at < png.size) {
        val length = readInt(png, at)
        val crc = CRC32().apply { update(png, at + 4, 4 + length) }.value.toInt()
        chunks += ParsedChunk(
            String(png, at + 4, 4, Charsets.US_ASCII),
            png.copyOfRange(at + 8, at + 8 + length),
            png.copyOfRange(at, at + CHUNK_OVERHEAD + length),
            crc == readInt(png, at + 8 + length),
        )
        at += CHUNK_OVERHEAD + length
    }
    return chunks
}

/** Reads [png] back sample by sample, with the plainest possible code. */
internal fun decode(png: ByteArray): Samples {
    val chunks = chunksOf(png)
    val header = chunks.first().data
    val width = readInt(header, 0)
    val height = readInt(header, 4)
    val depth = header[8].toInt()
    val colorType = header[9].toInt()
    val bits = depth * channelsOf(colorType)
    val stride = (width * bits + BITS_PER_BYTE - 1) / BITS_PER_BYTE
    val bytesPerPixel = maxOf(1, bits / BITS_PER_BYTE)
    val idat = chunks.filter { it.type == "IDAT" }.fold(ByteArray(0)) { acc, part -> acc + part.data }
    val inflated = InflaterInputStream(ByteArrayInputStream(idat)).readBytes()
    val values = IntArray(width * height * channelsOf(colorType))
    var previous = ByteArray(stride)
    for (y in 0 until height) {
        val type = inflated[y * (stride + 1)].toInt()
        val row = ByteArray(stride)
        for (i in 0 until stride) {
            val raw = inflated[y * (stride + 1) + 1 + i].toInt() and BYTE_MASK
            row[i] = (raw + predictor(type, at(row, i - bytesPerPixel), at(previous, i), at(previous, i - bytesPerPixel))).toByte()
        }
        for (s in 0 until width * channelsOf(colorType)) {
            var value = 0
            for (b in 0 until depth) {
                val bit = s * depth + b
                value = (value shl 1) or ((row[bit / BITS_PER_BYTE].toInt() shr (BITS_PER_BYTE - 1 - bit % BITS_PER_BYTE)) and 1)
            }
            values[y * width * channelsOf(colorType) + s] = value
        }
        previous = row
    }
    return Samples(width, height, depth, colorType, values)
}

/** The filter type of every row of a PNG, read back out of its image data. */
internal fun filterTypesOf(png: ByteArray): List<Int> {
    val chunks = chunksOf(png)
    val header = chunks.first().data
    val bits = header[8].toInt() * channelsOf(header[9].toInt())
    val stride = (readInt(header, 0) * bits + BITS_PER_BYTE - 1) / BITS_PER_BYTE
    val idat = chunks.filter { it.type == "IDAT" }.fold(ByteArray(0)) { acc, part -> acc + part.data }
    val inflated = InflaterInputStream(ByteArrayInputStream(idat)).readBytes()
    return (0 until readInt(header, 4)).map { inflated[it * (stride + 1)].toInt() }
}
