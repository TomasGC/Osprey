package app.osprey.media

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

private const val THOROUGH_LEVEL = 6
private const val FAST_LEVEL = 3
private const val THOROUGH_UP_TO_BYTES = 16 * 1024 * 1024

/** The zlib stream of a PNG's image data (#70). */
internal object PngZlib {

    /**
     * What the IDAT data [parts] inflate to, which must be exactly [size] bytes of a whole stream. The array has one
     * spare byte, so a stream that holds more than [size] is seen as such instead of being cut.
     */
    fun inflate(parts: List<PngChunk>, size: Int): ByteArray {
        val out = ByteArray(size + 1)
        val inflater = Inflater()
        try {
            val filled = parts.fold(0) { at, part -> at + drain(inflater, part, out, at) }
            if (!inflater.finished() || filled != size) refuse(PngTurn.DAMAGED)
        } finally {
            inflater.end()
        }
        return out
    }

    /**
     * The zlib level for scanlines of [size] bytes. Measured on real files: level 3 is three times faster than 6 and
     * no larger on a 12 MP photo, but leaves a flat screenshot 24% bigger than its original, where level 6 costs half
     * a second. So small pictures get the thorough level.
     */
    internal fun compressionLevel(size: Int): Int = if (size <= THOROUGH_UP_TO_BYTES) THOROUGH_LEVEL else FAST_LEVEL

    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(compressionLevel(data.size))
        try {
            // Presized to half the scanlines (a photo compresses to about 40%): the buffer rarely regrows.
            val out = ByteArrayOutputStream(data.size / 2)
            DeflaterOutputStream(out, deflater).use { it.write(data) }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun drain(inflater: Inflater, part: PngChunk, out: ByteArray, from: Int): Int {
        part.feed(inflater)
        var at = from
        while (!inflater.finished() && !inflater.needsInput()) {
            if (inflater.needsDictionary() || at == out.size) refuse(PngTurn.DAMAGED)
            at += inflater.inflate(out, at, out.size - at)
        }
        if (inflater.finished() && inflater.remaining > 0) refuse(PngTurn.DAMAGED)
        return at - from
    }
}
