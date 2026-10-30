package app.osprey.media

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

/**
 * The EXIF Orientation of a TIFF block (the Exif of a JPEG, the eXIf chunk of a PNG), found and changed in the
 * bytes without decoding anything (#61, #70).
 */
object TiffOrientation {

    /** Where the 2-byte value sits and how its bytes are ordered. */
    class Located(val offset: Int, val bigEndian: Boolean)

    /**
     * The Orientation value of IFD0 in the TIFF block starting at [tiff], or null when it has none. A block cut short,
     * or with an offset that points outside it, is "no value", never a crash.
     */
    fun locate(data: ByteArray, tiff: Int = 0): Located? = try {
        val byteOrder = data.u16(tiff, true)
        val bigEndian = byteOrder == TIFF_BIG_ENDIAN
        val hasHeader = (bigEndian || byteOrder == TIFF_LITTLE_ENDIAN) &&
            data.u16(tiff + TIFF_MAGIC_AT, bigEndian) == TIFF_MAGIC
        if (hasHeader) orientationEntry(data, tiff, bigEndian) else null
    } catch (ignored: IndexOutOfBoundsException) {
        null
    }

    fun read(data: ByteArray, located: Located): ExifOrientation =
        ExifOrientation.fromExif(data.u16(located.offset, located.bigEndian))

    /** The two bytes that hold [orientation] in a block of the given byte order. */
    fun valueBytes(orientation: ExifOrientation, bigEndian: Boolean): ByteArray {
        val value = (orientation.ordinal + 1).toByte()
        return if (bigEndian) byteArrayOf(0, value) else byteArrayOf(value, 0)
    }

    /** A copy of [data] holding [orientation] at [located] and differing in nothing else. */
    fun write(data: ByteArray, located: Located, orientation: ExifOrientation): ByteArray =
        data.copyOf().also { valueBytes(orientation, located.bigEndian).copyInto(it, located.offset) }

    /** The entry must be one short; the value's own bytes are read once so a file cut inside them has no value. */
    private fun orientationEntry(data: ByteArray, tiff: Int, bigEndian: Boolean): Located? {
        val ifd0 = tiff + data.u32(tiff + TIFF_IFD0_OFFSET_AT, bigEndian)
        val entry = (0 until data.u16(ifd0, bigEndian))
            .map { ifd0 + IFD_COUNT_SIZE + it * ENTRY_SIZE }
            .firstOrNull { data.u16(it, bigEndian) == TAG_ORIENTATION }
        return entry
            ?.takeIf {
                data.u16(it + ENTRY_TYPE_AT, bigEndian) == TYPE_SHORT && data.u32(it + ENTRY_COUNT_AT, bigEndian) == 1
            }
            ?.let { Located(it + ENTRY_VALUE_AT, bigEndian).also { located -> data.u16(located.offset, bigEndian) } }
    }
}
