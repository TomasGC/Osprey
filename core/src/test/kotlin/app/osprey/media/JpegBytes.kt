package app.osprey.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val TYPE_ASCII = 2
internal const val TYPE_SHORT = 3
internal const val TYPE_LONG = 4
private const val TAG_ORIENTATION = 0x0112
private const val ENTRY_SIZE = 12
private const val TIFF_HEADER_SIZE = 8
private const val TIFF_MAGIC = 42
private const val SEGMENT_LENGTH_BYTES = 2

private class Entry(val tag: Int, val type: Int, val count: Int, val value: Int)

private fun ByteBuffer.putEntry(entry: Entry) {
    putShort(entry.tag.toShort()).putShort(entry.type.toShort()).putInt(entry.count)
    if (entry.type == TYPE_SHORT) putShort(entry.value.toShort()).putShort(0) else putInt(entry.value)
}

internal fun tiff(bigEndian: Boolean, orientation: Int?, type: Int, count: Int, thumbnail: Int?): ByteArray {
    val ifd0 = buildList {
        add(Entry(0x0100, TYPE_LONG, 1, 4000))
        add(Entry(0x010F, TYPE_ASCII, 4, 0x41626300))
        if (orientation != null) add(Entry(TAG_ORIENTATION, type, count, orientation))
        add(Entry(0x0128, TYPE_SHORT, 1, 2))
    }
    val ifd0Size = 2 + ifd0.size * ENTRY_SIZE + 4
    val ifd1Size = if (thumbnail == null) 0 else 2 + ENTRY_SIZE + 4
    val buffer = ByteBuffer.allocate(TIFF_HEADER_SIZE + ifd0Size + ifd1Size)
        .order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
    buffer.put((if (bigEndian) "MM" else "II").toByteArray()).putShort(TIFF_MAGIC.toShort()).putInt(TIFF_HEADER_SIZE)
    buffer.putShort(ifd0.size.toShort())
    ifd0.forEach { buffer.putEntry(it) }
    buffer.putInt(if (thumbnail == null) 0 else TIFF_HEADER_SIZE + ifd0Size)
    if (thumbnail != null) {
        buffer.putShort(1)
        buffer.putEntry(Entry(TAG_ORIENTATION, TYPE_SHORT, 1, thumbnail))
        buffer.putInt(0)
    }
    return buffer.array()
}

internal fun segment(marker: Int, payload: ByteArray): ByteArray {
    val length = payload.size + SEGMENT_LENGTH_BYTES
    return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
}

internal fun exifSegment(
    bigEndian: Boolean = false,
    orientation: Int? = 6,
    type: Int = TYPE_SHORT,
    count: Int = 1,
    thumbnail: Int? = null,
): ByteArray = segment(0xE1, "Exif\u0000\u0000".toByteArray() + tiff(bigEndian, orientation, type, count, thumbnail))

internal fun jfifSegment(): ByteArray =
    segment(0xE0, "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray())

internal fun xmpSegment(): ByteArray = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>".toByteArray())

/**
 * A JPEG built by hand: SOI, the [leading] segments, an Exif segment (unless [withExif] is false) whose IFD0 holds
 * Orientation [orientation] of [type] and [count] (none when null) between other tags, an IFD1 holding [thumbnail] when given, a
 * vendor segment, a scan that contains the text `Exif` and a stuffed `FF 00`, EOI, then [trailer].
 */
internal fun jpegWith(
    bigEndian: Boolean = false,
    orientation: Int? = 6,
    type: Int = TYPE_SHORT,
    count: Int = 1,
    thumbnail: Int? = null,
    leading: List<ByteArray> = listOf(jfifSegment(), xmpSegment()),
    withExif: Boolean = true,
    trailer: ByteArray = "SEFT-trailer".toByteArray(),
): ByteArray {
    val exif = if (withExif) exifSegment(bigEndian, orientation, type, count, thumbnail) else ByteArray(0)
    val vendor = segment(0xE4, ByteArray(40) { 0xAE.toByte() })
    val scan = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 4, 0, 0) +
        "Exif".toByteArray() + byteArrayOf(0xFF.toByte(), 0x00, 0x12, 0x34)
    val end = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    val start = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    return leading.fold(start) { acc, part -> acc + part } + exif + vendor + scan + end + trailer
}
