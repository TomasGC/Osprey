package app.osprey.media

import app.osprey.actions.heicExtensions

private val TOP_LEVEL = listOf("ftyp", "mdat", "meta")
private const val ENTRY_SIZE = 12
private const val NEXT_IFD_SIZE = 4
private const val ENTRY_COUNT_SIZE = 2
private const val FIELD_OFFSET = 4
private const val IFD_OFFSET_FIELD = 4
private const val ONE_ENTRY = 1
private val CAMERA_NAME = Regex("""\d{8}_\d{6}|\d{4}-\d{2}-\d{2}|^(img|pxl|dsc|vid)[_-]?\d""", RegexOption.IGNORE_CASE)
private val LEAKS = linkedMapOf(
    "GPS" to Regex("GPS", RegexOption.IGNORE_CASE),
    "device" to Regex("samsung|SM-[A-Z]\\d|galaxy|iphone", RegexOption.IGNORE_CASE),
    "date" to Regex("""\d{4}[:\-/]\d\d[:\-/]\d\d"""),
    "XMP" to Regex("xpacket|xmlns|<x:xmp", RegexOption.IGNORE_CASE),
    "make or model" to Regex("""\bmake\b|\bmodel\b|software|artist""", RegexOption.IGNORE_CASE),
)

/**
 * What a real photo still carries that must not go into the fixtures repository (#76). A fixture holds its pixels and
 * one Orientation entry and nothing else: no vendor boxes after `meta`, no data in the Exif item beyond the entry, no
 * GPS, device, date or XMP text in the metadata, and a name that is not the camera's. The colour profile's own text
 * (the same on every phone of a make) is left out of the text scan.
 */
internal object FixtureScrub {

    fun problems(name: String, file: ByteArray): List<String> {
        val found = mutableListOf<String>()
        if (CAMERA_NAME.containsMatchIn(name)) found += "the name looks like a camera file name or a capture date"
        val extension = name.substringAfterLast('.').lowercase()
        return found + when (extension) {
            "jpg", "jpeg" -> JpegScrub.problems(file)
            "png" -> PngScrub.problems(file)
            else -> if (".$extension" in heicExtensions) heicProblems(file) else listOf("unknown fixture format: .$extension")
        }
    }

    private fun heicProblems(file: ByteArray): List<String> {
        val found = mutableListOf<String>()
        val layout = try {
            HeicLayout.of(file)
        } catch (refusal: HeicRefusal) {
            return found + "not a readable HEIC: ${refusal.reason}"
        }
        val unexpected = boxesIn(file, 0, file.size).map { it.type }.filter { it !in TOP_LEVEL }
        if (unexpected.isNotEmpty()) found += "unexpected top-level boxes: ${unexpected.joinToString(", ")}"
        val exif = exifRange(file, layout)
        found += exifProblems(file, layout, exif)
        val text = String(metadata(file, layout), Charsets.ISO_8859_1)
        LEAKS.forEach { (label, pattern) -> if (pattern.containsMatchIn(text)) found += "$label text found in the metadata" }
        return found
    }

    private fun exifRange(file: ByteArray, layout: HeicLayout): IntRange? {
        val at = (layout.exif as? ExifTarget.At)?.offset ?: return null
        return layout.extents.mapNotNull { it.range(file, layout.idat) }.firstOrNull { at in it }
    }

    private fun exifProblems(file: ByteArray, layout: HeicLayout, exif: IntRange?): List<String> {
        if (layout.exif == ExifTarget.None) return emptyList()
        if (exif == null) return listOf("the Exif item has no Orientation entry, so it cannot be checked")
        val tiff = exif.first + FIELD_OFFSET + file.u32(exif.first, true)
        val big = file[tiff] != 'I'.code.toByte()
        val ifd = tiff + file.u32(tiff + IFD_OFFSET_FIELD, big)
        val entries = file.u16(ifd, big)
        val problems = mutableListOf<String>()
        if (entries != ONE_ENTRY) problems += "the Exif holds $entries entries, only the orientation is allowed"
        val end = ifd + ENTRY_COUNT_SIZE + entries * ENTRY_SIZE + NEXT_IFD_SIZE
        if ((end..exif.last).any { file[it] != 0.toByte() }) problems += "the Exif holds data after the orientation entry"
        return problems
    }

    /** The `meta` box with the colour profile blanked out: its text is the maker's, the same on every phone. */
    private fun metadata(file: ByteArray, layout: HeicLayout): ByteArray {
        val meta = boxesIn(file, 0, file.size).first { it.type == "meta" }
        val blob = file.copyOfRange(meta.start, meta.end)
        val ipco = layout.properties?.ipco ?: return blob
        boxesIn(file, ipco.payloadAt, ipco.end).filter { it.type == "colr" }.forEach {
            blob.fill(0, it.start - meta.start, it.end - meta.start)
        }
        return blob
    }
}
