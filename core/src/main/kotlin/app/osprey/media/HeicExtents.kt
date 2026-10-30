package app.osprey.media

private const val FULL_BOX_HEADER = 4
private const val FLAGS_SIZE = 3
private const val SHORT_SIZE = 2
private const val METHOD_MASK = 0xF
private const val NIBBLE_BITS = 4
private const val ILOC_VERSION_WITH_METHOD = 1
private const val ILOC_VERSION_WITH_LONG_IDS = 2
const val HEIC_METHOD_FILE = 0
const val HEIC_METHOD_IDAT = 1

/** One extent of one item of an `iloc` box: [offset] and [length] counted from [base] as [method] says. */
internal class HeicExtent(val item: Long, val method: Int, val base: Long, val offset: Long, val length: Long) {

    /** Where the bytes are in [file] (counted from the file) or in its [idat] box; null if they cannot be. */
    fun range(file: ByteArray, idat: HeicBox?): IntRange? {
        val (origin, limit) = when (method) {
            HEIC_METHOD_FILE -> 0L to file.size.toLong()
            HEIC_METHOD_IDAT -> if (idat == null) return null else idat.payloadAt.toLong() to idat.end.toLong()
            else -> return null
        }
        val start = origin + base + offset
        return if (start + length > limit) null else start.toInt() until (start + length).toInt()
    }
}

/** Every extent of every item the `iloc` box lists; a field that cannot be read is damage. */
internal fun readExtents(file: ByteArray, iloc: HeicBox): List<HeicExtent> {
    val cursor = Cursor(file, iloc.payloadAt, iloc.end)
    val version = cursor.u8()
    cursor.skip(FLAGS_SIZE)
    val sizes = cursor.u8()
    val baseAndIndex = cursor.u8()
    val offsetSize = sizes shr NIBBLE_BITS
    val lengthSize = sizes and METHOD_MASK
    val baseSize = baseAndIndex shr NIBBLE_BITS
    val indexSize = if (version == 0) 0 else baseAndIndex and METHOD_MASK
    val longIds = version >= ILOC_VERSION_WITH_LONG_IDS
    val extents = mutableListOf<HeicExtent>()
    repeat((if (longIds) cursor.u32() else cursor.u16().toLong()).toInt()) {
        val item = if (longIds) cursor.u32() else cursor.u16().toLong()
        val method = if (version >= ILOC_VERSION_WITH_METHOD) cursor.u16() and METHOD_MASK else HEIC_METHOD_FILE
        cursor.skip(SHORT_SIZE) // data_reference_index
        val base = cursor.unsigned(baseSize)
        repeat(cursor.u16()) {
            cursor.skip(indexSize)
            extents += HeicExtent(item, method, base, cursor.unsigned(offsetSize), cursor.unsigned(lengthSize))
        }
    }
    return extents
}

internal const val HEIC_FULL_BOX_HEADER = FULL_BOX_HEADER
