package app.osprey.media

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/** The zlib stream of a PNG's image data (#70). */
internal object PngZlib {

    /**
     * What the IDAT data [parts] inflate to, which must be exactly [size] bytes of a whole stream. The array has one
     * spare byte, so a stream that holds more than [size] is seen as such instead of being cut.
     */
    fun inflate(parts: List<ByteArray>, size: Int): ByteArray {
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

    fun deflate(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun drain(inflater: Inflater, part: ByteArray, out: ByteArray, from: Int): Int {
        inflater.setInput(part)
        var at = from
        while (!inflater.finished() && !inflater.needsInput()) {
            if (inflater.needsDictionary() || at == out.size) refuse(PngTurn.DAMAGED)
            at += inflater.inflate(out, at, out.size - at)
        }
        if (inflater.finished() && inflater.remaining > 0) refuse(PngTurn.DAMAGED)
        return at - from
    }
}
