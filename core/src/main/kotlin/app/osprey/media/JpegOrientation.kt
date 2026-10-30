package app.osprey.media

private const val MARKER_PREFIX = 0xFF
private const val SOI = 0xFFD8
private const val SOS = 0xDA
private const val APP1 = 0xE1
private const val SOI_SIZE = 2
private const val SEGMENT_HEADER_SIZE = 4
private const val LENGTH_FIELD_SIZE = 2
private val EXIF_HEADER = "Exif\u0000\u0000".toByteArray()

/** The EXIF Orientation of a JPEG, found and changed in the bytes without decoding the image (#61). */
object JpegOrientation {

    /** Offset of the 2-byte Orientation value in IFD0 of the Exif segment, or null when the file has none. */
    fun valueOffset(jpeg: ByteArray): Int? = locate(jpeg)?.offset

    /** The orientation stored in [jpeg], or null when [valueOffset] finds none. */
    fun read(jpeg: ByteArray): ExifOrientation? = locate(jpeg)?.let { TiffOrientation.read(jpeg, it) }

    /**
     * A copy of [jpeg] whose Orientation is [transform] of the stored one and which differs in nothing else, or null
     * when there is no value to change.
     */
    fun rewrite(jpeg: ByteArray, transform: (ExifOrientation) -> ExifOrientation): ByteArray? {
        val located = locate(jpeg) ?: return null
        return TiffOrientation.write(jpeg, located, transform(TiffOrientation.read(jpeg, located)))
    }

    // A file cut short, or with an offset that points outside it, is "no value", never a crash.
    private fun locate(jpeg: ByteArray): TiffOrientation.Located? = try {
        if (jpeg.u16(0, true) == SOI) {
            segmentStarts(jpeg).firstOrNull { jpeg.isExif(it) }
                ?.let { TiffOrientation.locate(jpeg, it + SEGMENT_HEADER_SIZE + EXIF_HEADER.size) }
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
}
