package app.osprey.media

private const val LAST_LETTER = 3

private val CRITICAL = setOf("IHDR", "PLTE", "IDAT", "IEND")
private val ANIMATION = setOf("acTL", "fcTL", "fdAT")

// Ancillary chunks whose content does not depend on the picture's layout, so they survive a turn as they are.
private val CARRIED = setOf(
    "tRNS", "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", "cICP", "mDCV", "cLLI", "bKGD", "hIST", "pHYs", "sPLT", "tIME",
    "iTXt", "tEXt", "zTXt", "eXIf",
)

/** Checks that the chunks of a PNG are ones a turn can carry through, and says what the picture is (#70). */
internal object PngStructure {

    fun check(chunks: List<PngChunk>): PngHeader {
        val types = chunks.map { it.type }
        if (types.any { it in ANIMATION }) refuse(PngTurn.ANIMATED)
        if (types.first() != "IHDR" || types.count { it == "IHDR" } != 1 || chunks.last().size > 0) {
            refuse(PngTurn.DAMAGED)
        }
        if (!types.all { carries(it) }) refuse(PngTurn.UNKNOWN_CHUNK)
        val header = PngHeader.parse(chunks.first().data)
        if (!isOneRun(types, "IDAT")) refuse(PngTurn.DAMAGED)
        if (header.isPalette && "PLTE" !in types) refuse(PngTurn.DAMAGED)
        return header
    }

    // An unknown chunk is carried when it is ancillary (first letter lowercase) and safe to copy (last lowercase).
    private fun carries(type: String): Boolean =
        type in CRITICAL || type in CARRIED || (type.first().isLowerCase() && type[LAST_LETTER].isLowerCase())

    private fun isOneRun(types: List<String>, type: String): Boolean {
        val at = types.indices.filter { types[it] == type }
        return at.isNotEmpty() && at.last() - at.first() + 1 == at.size
    }
}
