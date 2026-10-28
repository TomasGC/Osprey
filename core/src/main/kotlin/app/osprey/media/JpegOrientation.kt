package app.osprey.media

private const val MARKER_PREFIX = 0xFF
private const val SOI = 0xFFD8
private const val SOS = 0xDA
private const val APP1 = 0xE1
private const val SOI_SIZE = 2
private const val SEGMENT_HEADER_SIZE = 4
private const val LENGTH_FIELD_SIZE = 2
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val BITS_PER_SHORT = 16
private const val TIFF_BIG_ENDIAN = 0x4D4D // "MM"
private const val TIFF_LITTLE_ENDIAN = 0x4949 // "II"
private const val TIFF_MAGIC = 42
private const val TIFF_MAGIC_AT = 2
private const val TIFF_IFD0_OFFSET_AT = 4
private const val TAG_ORIENTATION = 0x0112
private const val TYPE_SHORT = 3
private const val IFD_COUNT_SIZE = 2
private const val ENTRY_SIZE = 12
private const val ENTRY_TYPE_AT = 2
private const val ENTRY_COUNT_AT = 4
private const val ENTRY_VALUE_AT = 8
private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray()

/** The EXIF Orientation of a JPEG, found and changed in the bytes without decoding the image (#61). */
object JpegOrientation {

    /** Where the value sits and how its two bytes are ordered. */
    private class Located(val offset: Int, val bigEndian: Boolean)

    /** Offset of the 2-byte Orientation value in IFD0 of the Exif segment, or null when the file has none. */
    fun valueOffset(jpeg: ByteArray): Int? = locate(jpeg)?.offset

    /** The orientation stored in [jpeg], or null when [valueOffset] finds none. */
    fun read(jpeg: ByteArray): ExifOrientation? =
        locate(jpeg)?.let { ExifOrientation.fromExif(jpeg.u16(it.offset, it.bigEndian)) }

    /**
     * A copy of [jpeg] whose Orientation is [transform] of the stored one and which differs in nothing else, or null
     * when there is no value to change.
     */
    fun rewrite(jpeg: ByteArray, transform: (ExifOrientation) -> ExifOrientation): ByteArray? {
        val located = locate(jpeg) ?: return null
        val stored = ExifOrientation.fromExif(jpeg.u16(located.offset, located.bigEndian))
        val value = (transform(stored).ordinal + 1).toByte()
        val (high, low) = if (located.bigEndian) 0.toByte() to value else value to 0.toByte()
        return jpeg.copyOf().also {
            it[located.offset] = high
            it[located.offset + 1] = low
        }
    }

    // A file cut short, or with an offset that points outside it, is "no value", never a crash.
    private fun locate(jpeg: ByteArray): Located? = try {
        if (jpeg.u16(0, true) == SOI) {
            segmentStarts(jpeg).firstOrNull { jpeg.isExif(it) }?.let { orientationIn(jpeg, it) }
        } else {
            null
        }
    } catch (ignored: IndexOutOfBoundsException) {
        null
    }

    /** The start of each segment up to the scan. */
    private fun segmentStarts(jpeg: ByteArray): Sequence<Int> =
        generateSequence(SOI_SIZE) { start -> start + LENGTH_FIELD_SIZE + jpeg.u16(start + LENGTH_FIELD_SIZE, true) }
            .takeWhile { jpeg.u8(it) == MARKER_PREFIX && jpeg.u8(it + 1) != SOS }

    private fun ByteArray.isExif(start: Int): Boolean =
        u8(start + 1) == APP1 &&
            EXIF_HEADER.indices.all { u8(start + SEGMENT_HEADER_SIZE + it) == EXIF_HEADER[it].toInt() }

    /** The Orientation value of IFD0 in the TIFF block of the Exif segment starting at [segment]. */
    private fun orientationIn(jpeg: ByteArray, segment: Int): Located? {
        val tiff = segment + SEGMENT_HEADER_SIZE + EXIF_HEADER.size
        val byteOrder = jpeg.u16(tiff, true)
        val bigEndian = byteOrder == TIFF_BIG_ENDIAN
        val hasHeader = (bigEndian || byteOrder == TIFF_LITTLE_ENDIAN) &&
            jpeg.u16(tiff + TIFF_MAGIC_AT, bigEndian) == TIFF_MAGIC
        return if (hasHeader) orientationEntry(jpeg, tiff, bigEndian) else null
    }

    /** The entry must be one short; the value's own bytes are read once so a file cut inside them has no value. */
    private fun orientationEntry(jpeg: ByteArray, tiff: Int, bigEndian: Boolean): Located? {
        val ifd0 = tiff + jpeg.u32(tiff + TIFF_IFD0_OFFSET_AT, bigEndian)
        val entry = (0 until jpeg.u16(ifd0, bigEndian))
            .map { ifd0 + IFD_COUNT_SIZE + it * ENTRY_SIZE }
            .firstOrNull { jpeg.u16(it, bigEndian) == TAG_ORIENTATION }
        return entry
            ?.takeIf {
                jpeg.u16(it + ENTRY_TYPE_AT, bigEndian) == TYPE_SHORT && jpeg.u32(it + ENTRY_COUNT_AT, bigEndian) == 1
            }
            ?.let { Located(it + ENTRY_VALUE_AT, bigEndian).also { located -> jpeg.u16(located.offset, bigEndian) } }
    }

}

private fun ByteArray.u8(at: Int): Int = this[at].toInt() and BYTE_MASK

private fun ByteArray.u16(at: Int, bigEndian: Boolean): Int =
    if (bigEndian) (u8(at) shl BITS_PER_BYTE) or u8(at + 1) else u8(at) or (u8(at + 1) shl BITS_PER_BYTE)

private fun ByteArray.u32(at: Int, bigEndian: Boolean): Int {
    val first = u16(at, bigEndian)
    val second = u16(at + LENGTH_FIELD_SIZE, bigEndian)
    return if (bigEndian) (first shl BITS_PER_SHORT) or second else first or (second shl BITS_PER_SHORT)
}
