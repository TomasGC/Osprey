package app.osprey.media

import java.util.zip.CRC32
import java.util.zip.Inflater

private val SIGNATURE = "\u0089PNG\r\n\u001a\n".toByteArray(Charsets.ISO_8859_1)
private const val LENGTH_SIZE = 4
private const val TYPE_SIZE = 4
private const val CRC_SIZE = 4
private const val OVERHEAD = LENGTH_SIZE + TYPE_SIZE + CRC_SIZE
private const val END_TYPE = "IEND"

/**
 * One PNG chunk: its type and its data, which stays in the bytes of the file it was read from, because the image data
 * is most of a big photo and must not exist twice in memory.
 */
internal class PngChunk(val type: String, private val source: ByteArray, private val offset: Int, val size: Int) {

    constructor(type: String, data: ByteArray) : this(type, data, 0, data.size)

    /** A copy of the data: for the small chunks, never the image data (see [feed]). */
    val data: ByteArray get() = source.copyOfRange(offset, offset + size)

    /** Hands the data to [inflater] without copying it. */
    fun feed(inflater: Inflater) = inflater.setInput(source, offset, size)

    /** Writes length, type, data and checksum into [out] from [at]; returns where the next chunk starts. */
    fun writeTo(out: ByteArray, at: Int): Int {
        val dataAt = at + LENGTH_SIZE + TYPE_SIZE
        out.putU32(at, size)
        type.toByteArray(Charsets.US_ASCII).copyInto(out, at + LENGTH_SIZE)
        source.copyInto(out, dataAt, offset, offset + size)
        val crc = CRC32().apply { update(out, at + LENGTH_SIZE, TYPE_SIZE + size) }.value.toInt()
        out.putU32(dataAt + size, crc)
        return at + OVERHEAD + size
    }
}

/** Reads a PNG into its chunks and writes chunks back into a file (#70). */
internal object PngChunks {

    /** The chunks of [png], each with its checksum verified; refuses anything that is not exactly one whole PNG. */
    fun read(png: ByteArray): List<PngChunk> {
        if (png.size < SIGNATURE.size || !png.copyOf(SIGNATURE.size).contentEquals(SIGNATURE)) refuse(PngTurn.DAMAGED)
        val chunks = mutableListOf<PngChunk>()
        var at = SIGNATURE.size
        while (chunks.lastOrNull()?.type != END_TYPE) {
            val chunk = chunkAt(png, at)
            chunks += chunk
            at += OVERHEAD + chunk.size
        }
        if (at != png.size) refuse(PngTurn.TRAILING_DATA)
        return chunks
    }

    fun write(chunks: List<PngChunk>): ByteArray {
        val out = ByteArray(SIGNATURE.size + chunks.sumOf { OVERHEAD + it.size })
        SIGNATURE.copyInto(out)
        chunks.fold(SIGNATURE.size) { at, chunk -> chunk.writeTo(out, at) }
        return out
    }

    private fun chunkAt(png: ByteArray, at: Int): PngChunk {
        if (png.size - at < OVERHEAD) refuse(PngTurn.DAMAGED)
        val length = png.u32(at, true)
        if (length < 0 || length > png.size - at - OVERHEAD) refuse(PngTurn.DAMAGED)
        val type = String(png, at + LENGTH_SIZE, TYPE_SIZE, Charsets.US_ASCII)
        if (!type.all { it.isLetter() }) refuse(PngTurn.DAMAGED)
        val dataAt = at + LENGTH_SIZE + TYPE_SIZE
        val crc = CRC32().apply { update(png, at + LENGTH_SIZE, TYPE_SIZE + length) }.value.toInt()
        if (crc != png.u32(dataAt + length, true)) refuse(PngTurn.DAMAGED)
        return PngChunk(type, png, dataAt, length)
    }
}
