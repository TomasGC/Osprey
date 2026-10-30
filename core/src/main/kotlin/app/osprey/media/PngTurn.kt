package app.osprey.media

import java.util.zip.DataFormatException

private const val MEBIBYTE = 1024 * 1024
private const val PHYS_AXIS_SIZE = 4
private const val PHYS_SIZE = 9

/**
 * Turns the pixels of a PNG a quarter at a time without decoding them to colours (#70): the scanlines are unfiltered,
 * whole pixels are moved, and the rows are filtered and compressed again. Every sample, palette entry and bit depth is
 * kept exactly, and every other chunk is carried over. A picture that holds an EXIF orientation is turned together
 * with it and the value is set upright, so the file reads the same whether or not a viewer honours it.
 */
object PngTurn {

    /** The most bytes of scanlines (filter bytes included) one turn holds in memory at a time; it holds two. */
    const val MAX_RAW_BYTES = 48 * MEBIBYTE

    const val DAMAGED = "This PNG is damaged, so it was left as it is"
    const val INTERLACED = "This PNG is interlaced, which cannot be saved turned"
    const val ANIMATED = "This PNG is animated, which cannot be saved turned"
    const val TOO_BIG = "This PNG is too big to be turned on this device"
    const val TRAILING_DATA = "This PNG has extra data after its end, so it was left as it is"
    const val UNKNOWN_CHUNK = "This PNG holds a part Osprey cannot carry through a turn"

    /**
     * [png] turned [quarterTurns] clockwise (negative: counterclockwise), whole turns changing nothing, or the reason
     * it cannot be. A picture needing more than [maxRawBytes] bytes of scanlines is refused before it is read.
     */
    fun turn(png: ByteArray, quarterTurns: Int, maxRawBytes: Int = MAX_RAW_BYTES): PngTurnResult = try {
        turned(png, quarterTurns, maxRawBytes)
    } catch (refusal: PngRefusal) {
        PngTurnResult.Refused(refusal.reason)
    } catch (ignored: DataFormatException) {
        PngTurnResult.Refused(DAMAGED)
    }

    private fun turned(png: ByteArray, quarterTurns: Int, maxRawBytes: Int): PngTurnResult {
        val chunks = PngChunks.read(png)
        val header = PngStructure.check(chunks)
        val stored = storedOrientation(chunks)
        val orientation = stored.turnedBy(quarterTurns)
        if (stored == ExifOrientation.NORMAL && orientation == ExifOrientation.NORMAL) return PngTurnResult.Unchanged
        val image = if (orientation == ExifOrientation.NORMAL) {
            null
        } else {
            turnedImage(chunks, header, orientation, maxRawBytes)
        }
        return PngTurnResult.Turned(PngChunks.write(rebuilt(chunks, header, orientation, image)))
    }

    private fun storedOrientation(chunks: List<PngChunk>): ExifOrientation {
        val data = (chunks.firstOrNull { it.type == "eXIf" } ?: return ExifOrientation.NORMAL).data
        val located = TiffOrientation.locate(data) ?: return ExifOrientation.NORMAL
        return TiffOrientation.read(data, located)
    }

    /** The compressed image data of the turned picture. The picture is held once, then twice, then once, then not. */
    private fun turnedImage(
        chunks: List<PngChunk>,
        header: PngHeader,
        orientation: ExifOrientation,
        limit: Int,
    ): ByteArray {
        if (header.rawSize > limit) refuse(TOO_BIG)
        val target = if (orientation.swapsDimensions) header.swapped() else header
        val scanlines = orient(chunks, header, target, orientation)
        PngScanlines.filter(scanlines, target)
        return PngZlib.deflate(scanlines)
    }

    // Its own function so the unfiltered copy is out of reach, and collectable, once the turned one exists.
    private fun orient(
        chunks: List<PngChunk>,
        header: PngHeader,
        target: PngHeader,
        orientation: ExifOrientation,
    ): ByteArray {
        val raw = PngZlib.inflate(chunks.filter { it.type == "IDAT" }, header.rawSize.toInt())
        PngScanlines.unfilter(raw, header)
        return PngPixels.orient(raw, header, target, orientation)
    }

    /** The chunks of the turned file: the new header and image data, and what a turn changes in the others. */
    private fun rebuilt(
        chunks: List<PngChunk>,
        header: PngHeader,
        orientation: ExifOrientation,
        image: ByteArray?,
    ): List<PngChunk> {
        val swaps = orientation.swapsDimensions
        var imageWritten = false
        return chunks.mapNotNull { chunk ->
            when {
                chunk.type == "IHDR" && swaps -> PngChunk(chunk.type, header.swapped().toBytes(chunk.data))
                chunk.type == "pHYs" && swaps -> PngChunk(chunk.type, withAxesSwapped(chunk.data))
                chunk.type == "eXIf" -> PngChunk(chunk.type, upright(chunk.data))
                chunk.type == "IDAT" && image != null -> {
                    val first = !imageWritten
                    imageWritten = true
                    if (first) PngChunk(chunk.type, image) else null
                }
                else -> chunk
            }
        }
    }

    private fun withAxesSwapped(data: ByteArray): ByteArray {
        if (data.size != PHYS_SIZE) return data
        return data.copyOf().also {
            data.copyInto(it, 0, PHYS_AXIS_SIZE, 2 * PHYS_AXIS_SIZE)
            data.copyInto(it, PHYS_AXIS_SIZE, 0, PHYS_AXIS_SIZE)
        }
    }

    private fun upright(data: ByteArray): ByteArray {
        val located = TiffOrientation.locate(data) ?: return data
        return TiffOrientation.write(data, located, ExifOrientation.NORMAL)
    }
}
