package app.osprey.media

import java.time.Instant

private const val HEADER = 8
private const val LARGE_HEADER = 16
private const val SIZE_FIELD = 4
private const val TYPE_FIELD = 4
private const val LARGE_SIZE_AT = 8
private const val VERSION_AND_FLAGS = 4
private const val HANDLER_TYPE_AT = 8
private const val CREATION_AT = 4
private const val MAX_BYTES = 16 * 1024 * 1024
private const val MAX_BEAM_BYTES = 32
private const val MAX_ENCODER_BYTES = 64
private const val MAX_TRACKS = 2
private const val SECONDS_FROM_1904_TO_1970 = 2_082_844_800L
private const val INT_MASK = 0xFFFFFFFFL
private const val INT_BITS = 32
private const val VERSION_ONE = 1
private const val ENCODER_TAG = "©too"

// WhatsApp writes a `beam` atom of a few numbers after `ftyp`: not part of the MP4 standard, and no text in it.
private val TOP_LEVEL = setOf("ftyp", "free", "mdat", "moov", "beam")
private val MOOV_KEPT = setOf("mvhd", "trak", "udta")
private val TRACK_KEPT = setOf("tkhd", "edts", "mdia")
private val MEDIA_KEPT = setOf("mdhd", "hdlr", "minf")
private val HANDLERS = setOf("vide", "soun")

/**
 * What an MP4 fixture still carries (#131). A clip keeps the camera's own name and its container's creation time; the
 * rest must be the picture and the sound: the top-level atoms are `ftyp`, `free`, `mdat`, `moov` (and WhatsApp's
 * small `beam`), `moov` holds `mvhd`, at most a video and an audio track, and a `udta` that has the encoder tag and
 * nothing else; any location, device or other tag is reported, and so is a body over 16 MB.
 */
internal object Mp4Scrub {

    private class Atom(val type: String, val start: Int, val payload: Int, val end: Int)

    fun problems(file: ByteArray): List<String> {
        val top = atoms(file, 0, file.size) ?: return listOf("not a readable MP4: an atom runs past the end")
        val found = mutableListOf<String>()
        if (file.size > MAX_BYTES) found += "the clip is larger than 16 MB"
        if (top.none { it.type == "ftyp" }) return found + "not a readable MP4: no ftyp atom"
        val moov = top.firstOrNull { it.type == "moov" } ?: return found + "not a readable MP4: no moov atom"
        val unexpected = top.map { it.type }.filter { it !in TOP_LEVEL }
        if (unexpected.isNotEmpty()) found += "unexpected top-level atoms: ${unexpected.joinToString(", ")}"
        if (top.any { it.type == "beam" && it.end - it.start > MAX_BEAM_BYTES }) {
            found += "the beam atom is larger than $MAX_BEAM_BYTES bytes"
        }
        return found + moovProblems(file, moov)
    }

    /** The creation time in `mvhd`, or null when the clip has none (WhatsApp's) or cannot be read. */
    fun creationTime(file: ByteArray): Instant? {
        val mvhd = children(file, moovOf(file) ?: return null).firstOrNull { it.type == "mvhd" } ?: return null
        val seconds = if (file.u8(mvhd.payload) == VERSION_ONE) {
            (file.u32(mvhd.payload + CREATION_AT, true).toLong() and INT_MASK shl INT_BITS) or
                (file.u32(mvhd.payload + CREATION_AT + SIZE_FIELD, true).toLong() and INT_MASK)
        } else {
            file.u32(mvhd.payload + CREATION_AT, true).toLong() and INT_MASK
        }
        return if (seconds == 0L) null else Instant.ofEpochSecond(seconds - SECONDS_FROM_1904_TO_1970)
    }

    /** The `moov` atom as text, for the scan for device and GPS strings; empty when there is none. */
    fun moovText(file: ByteArray): String {
        val moov = moovOf(file) ?: return ""
        return String(file, moov.start, moov.end - moov.start, Charsets.ISO_8859_1)
    }

    private fun moovOf(file: ByteArray): Atom? = atoms(file, 0, file.size)?.firstOrNull { it.type == "moov" }

    private fun children(file: ByteArray, parent: Atom): List<Atom> = atoms(file, parent.payload, parent.end).orEmpty()

    private fun moovProblems(file: ByteArray, moov: Atom): List<String> {
        val kids = atoms(file, moov.payload, moov.end) ?: return listOf("not a readable MP4: an atom in moov is cut")
        val found = mutableListOf<String>()
        val unexpected = kids.map { it.type }.filter { it !in MOOV_KEPT }
        if (unexpected.isNotEmpty()) found += "unexpected atoms in moov: ${unexpected.joinToString(", ")}"
        val tracks = kids.filter { it.type == "trak" }
        if (tracks.size > MAX_TRACKS) found += "${tracks.size} tracks, only a video and an audio track are allowed"
        found += trackProblems(file, tracks)
        kids.filter { it.type == "udta" }.forEach { found += udtaProblems(file, it) }
        return found
    }

    private fun trackProblems(file: ByteArray, tracks: List<Atom>): List<String> {
        val found = mutableListOf<String>()
        val handlers = mutableListOf<String>()
        tracks.forEach { track ->
            val kids = children(file, track)
            val unexpected = kids.map { it.type }.filter { it !in TRACK_KEPT }
            if (unexpected.isNotEmpty()) found += "unexpected atoms in a trak: ${unexpected.joinToString(", ")}"
            kids.filter { it.type == "mdia" }.forEach { mdia ->
                val media = children(file, mdia)
                val inMedia = media.map { it.type }.filter { it !in MEDIA_KEPT }
                if (inMedia.isNotEmpty()) found += "unexpected atoms in a trak's mdia: ${inMedia.joinToString(", ")}"
                media.filter { it.type == "hdlr" }.forEach { handlers += handlerOf(file, it) }
            }
        }
        handlers.filter { it !in HANDLERS }.forEach { found += "a track of handler type '$it': only vide and soun" }
        handlers.filter { it in HANDLERS }.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { found += "more than one '$it' track" }
        return found
    }

    private fun handlerOf(file: ByteArray, hdlr: Atom): String {
        val at = hdlr.payload + HANDLER_TYPE_AT
        return if (at + TYPE_FIELD > hdlr.end) "" else String(file, at, TYPE_FIELD, Charsets.ISO_8859_1)
    }

    private fun udtaProblems(file: ByteArray, udta: Atom): List<String> {
        val kids = children(file, udta)
        return if (kids.size == 1 && kids[0].type == "meta" && encoderOnly(file, kids[0])) {
            emptyList()
        } else {
            listOf("udta holds more than the encoder tag: ${kids.joinToString(", ") { it.type }}")
        }
    }

    private fun encoderOnly(file: ByteArray, meta: Atom): Boolean {
        val kids = atoms(file, meta.payload + VERSION_AND_FLAGS, meta.end) ?: return false
        val ilst = kids.singleOrNull { it.type == "ilst" } ?: return false
        if (kids.any { it.type != "hdlr" && it.type != "ilst" } || ilst.end - ilst.start > MAX_ENCODER_BYTES) {
            return false
        }
        return atoms(file, ilst.payload, ilst.end)?.all { it.type == ENCODER_TAG } == true
    }

    /** The atoms laid end to end from [from] to [to], or null when one is cut or has a size that cannot be right. */
    private fun atoms(file: ByteArray, from: Int, to: Int): List<Atom>? {
        val found = mutableListOf<Atom>()
        var at = from
        while (at < to) {
            if (at + HEADER > to) return null
            val size = file.u32(at, true).toLong() and INT_MASK
            val header = if (size == 1L) LARGE_HEADER else HEADER
            val total = when {
                size == 0L -> (to - at).toLong()
                size == 1L && at + LARGE_HEADER <= to -> large(file, at + LARGE_LENGTH_AT)
                else -> size
            }
            if (total < header || at + total > to) return null
            val type = String(file, at + SIZE_FIELD, TYPE_FIELD, Charsets.ISO_8859_1)
            found += Atom(type, at, at + header, (at + total).toInt())
            at += total.toInt()
        }
        return found
    }

    private fun large(file: ByteArray, at: Int): Long = (file.u32(at, true).toLong() and INT_MASK shl INT_BITS) or
        (file.u32(at + SIZE_FIELD, true).toLong() and INT_MASK)

    private const val LARGE_LENGTH_AT = LARGE_SIZE_AT
}
