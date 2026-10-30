package app.osprey.media

private const val BOX_HEADER = 8
private const val LARGE_BOX_HEADER = 16
private const val LARGE_SIZE_MARKER = 1L
private const val TO_END_OF_FILE_MARKER = 0L
private const val UNSIGNED_INT = 0xFFFFFFFFL
private const val INT_BITS = 32
private const val LONGEST_FIELD = 8
private const val TYPE_SIZE = 4

/** One box of an ISO base media file: its type, where it starts, where its payload starts and where it ends. */
internal class HeicBox(val type: String, val start: Int, val payloadAt: Int, val end: Int)

/** Reads fields from [at] up to [end] of [file]; reading past [end] is damage, never an exception. */
internal class Cursor(private val file: ByteArray, var at: Int, private val end: Int) {

    private fun take(count: Int): Int {
        if (at + count > end) refuseHeic(HeicTurn.DAMAGED)
        return at.also { at += count }
    }

    fun u8(): Int = file.u8(take(1))

    fun u16(): Int = file.u16(take(2), true)

    fun u32(): Long = file.u32(take(TYPE_SIZE), true).toLong() and UNSIGNED_INT

    /** A big-endian unsigned number of [size] bytes (0 to 8). */
    fun unsigned(size: Int): Long {
        if (size > LONGEST_FIELD) refuseHeic(HeicTurn.DAMAGED)
        var value = 0L
        repeat(size) { value = (value shl Byte.SIZE_BITS) or u8().toLong() }
        return value
    }

    fun skip(count: Int) {
        take(count)
    }

    fun text(): String = String(file, take(TYPE_SIZE), TYPE_SIZE, Charsets.ISO_8859_1)
}

/** The boxes that fill [from] to [to] exactly; a box that does not fit, or bytes too few for a header, is damage. */
internal fun boxesIn(file: ByteArray, from: Int, to: Int): List<HeicBox> {
    val boxes = mutableListOf<HeicBox>()
    var at = from
    while (at < to) {
        val cursor = Cursor(file, at, to)
        val declared = cursor.u32()
        val type = cursor.text()
        val (size, header) = when (declared) {
            LARGE_SIZE_MARKER -> ((cursor.u32() shl INT_BITS) or cursor.u32()) to LARGE_BOX_HEADER
            TO_END_OF_FILE_MARKER -> (to - at).toLong() to BOX_HEADER
            else -> declared to BOX_HEADER
        }
        if (size < header || size > to - at) refuseHeic(HeicTurn.DAMAGED)
        boxes += HeicBox(type, at, at + header, at + size.toInt())
        at += size.toInt()
    }
    return boxes
}
