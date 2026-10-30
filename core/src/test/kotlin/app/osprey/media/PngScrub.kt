package app.osprey.media

private const val SIGNATURE_SIZE = 8
private const val CHUNK_OVERHEAD = 12
private const val TYPE_AT = 4
private const val TYPE_SIZE = 4
private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

/** Chunks a PNG may keep: the picture and its colour description; text, Exif and time chunks are not among them. */
private val ALLOWED = setOf("IHDR", "PLTE", "IDAT", "IEND", "sRGB", "gAMA", "cHRM", "pHYs")

/** What a PNG fixture still carries (#76): any chunk beyond the picture and its colour. */
internal object PngScrub {

    fun problems(file: ByteArray): List<String> {
        if (file.size < SIGNATURE_SIZE || !file.copyOf(SIGNATURE_SIZE).contentEquals(SIGNATURE)) {
            return listOf("not a readable PNG: no signature")
        }
        val unexpected = mutableListOf<String>()
        var at = SIGNATURE_SIZE
        while (at < file.size) {
            val length = if (at + CHUNK_OVERHEAD <= file.size) file.u32(at, true) else -1
            if (length < 0 || at + CHUNK_OVERHEAD + length > file.size) return named(unexpected) + "not a readable PNG: a chunk is cut short"
            val type = String(file, at + TYPE_AT, TYPE_SIZE, Charsets.ISO_8859_1)
            if (type !in ALLOWED) unexpected += type
            at += CHUNK_OVERHEAD + length
        }
        return named(unexpected)
    }

    private fun named(unexpected: List<String>) =
        if (unexpected.isEmpty()) emptyList() else listOf("unexpected PNG chunks: ${unexpected.joinToString(", ")}")
}
