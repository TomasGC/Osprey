package app.osprey.media

private const val MARKER_PREFIX = 0xFF
private const val START_OF_IMAGE = 0xD8
private const val START_OF_SCAN = 0xDA
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val SEGMENT_HEADER = 2
private const val LENGTH_FIELD = 2
private const val MARKER_SIZE = 2
private const val EXIF_PREFIX_SIZE = 6
private const val TIFF_IFD_FIELD = 4
private const val ENTRY_COUNT_SIZE = 2
private const val ENTRY_SIZE = 12
private const val NEXT_IFD_SIZE = 4
private const val ORIENTATION_TAG = 0x0112
private val EXIF_PREFIX = "Exif\u0000\u0000".toByteArray()
private val JFIF_PREFIX = "JFIF\u0000".toByteArray()

/** Image and table markers a JPEG may keep: the picture's own segments and JFIF's header. */
private val PICTURE_SEGMENTS = setOf(0xC0, 0xC1, 0xC2, 0xC4, 0xDB, 0xDD)

/**
 * What a JPEG fixture still carries (#76). Its header may hold the picture's own segments, a JFIF header and one Exif
 * segment with a single Orientation entry; a comment, an XMP packet, any other segment or more Exif is reported.
 */
internal object JpegScrub {

    fun problems(file: ByteArray): List<String> {
        if (file.size < MARKER_SIZE || file.u8(0) != MARKER_PREFIX || file.u8(1) != START_OF_IMAGE) {
            return listOf("not a readable JPEG: no start marker")
        }
        val found = mutableListOf<String>()
        val unexpected = mutableListOf<String>()
        var at = MARKER_SIZE
        while (true) {
            if (at + MARKER_SIZE + LENGTH_FIELD > file.size || file.u8(at) != MARKER_PREFIX) {
                return found + "not a readable JPEG: cut before its picture"
            }
            val marker = file.u8(at + 1)
            if (marker == START_OF_SCAN) break
            val length = file.u16(at + MARKER_SIZE, true)
            val end = at + MARKER_SIZE + length
            if (length < LENGTH_FIELD || end > file.size) return found + "not a readable JPEG: a segment runs past the end"
            val body = at + SEGMENT_HEADER + LENGTH_FIELD
            when {
                marker in PICTURE_SEGMENTS -> Unit
                marker == APP0 && file.startsWith(JFIF_PREFIX, body) -> Unit
                marker == APP1 && file.startsWith(EXIF_PREFIX, body) -> found += exifProblems(file, body, end)
                else -> unexpected += "FF%02X".format(marker)
            }
            at = end
        }
        if (unexpected.isNotEmpty()) found += "unexpected JPEG segments: ${unexpected.joinToString(", ")}"
        return found
    }

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int) =
        at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }

    @Suppress("SwallowedException") // an Exif that cannot be read cannot be checked, which is what is reported
    private fun exifProblems(file: ByteArray, body: Int, end: Int): List<String> = try {
        val tiff = body + EXIF_PREFIX_SIZE
        val big = file[tiff] != 'I'.code.toByte()
        val ifd = tiff + file.u32(tiff + TIFF_IFD_FIELD, big)
        val entries = file.u16(ifd, big)
        when {
            entries != 1 -> listOf("the Exif holds $entries entries, only the orientation is allowed")
            file.u16(ifd + ENTRY_COUNT_SIZE, big) != ORIENTATION_TAG -> listOf("the Exif has no Orientation entry")
            ifd + ENTRY_COUNT_SIZE + ENTRY_SIZE + NEXT_IFD_SIZE < end -> listOf("the Exif holds data after the orientation entry")
            else -> emptyList()
        }
    } catch (ignored: IndexOutOfBoundsException) {
        listOf("the Exif cannot be read, so it cannot be checked")
    }
}
