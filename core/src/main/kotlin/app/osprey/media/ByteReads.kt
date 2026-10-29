package app.osprey.media

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val BITS_PER_SHORT = 16
private const val SHORT_SIZE = 2

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and BYTE_MASK

internal fun ByteArray.u16(at: Int, bigEndian: Boolean): Int =
    if (bigEndian) (u8(at) shl BITS_PER_BYTE) or u8(at + 1) else u8(at) or (u8(at + 1) shl BITS_PER_BYTE)

internal fun ByteArray.u32(at: Int, bigEndian: Boolean): Int {
    val first = u16(at, bigEndian)
    val second = u16(at + SHORT_SIZE, bigEndian)
    return if (bigEndian) (first shl BITS_PER_SHORT) or second else first or (second shl BITS_PER_SHORT)
}
