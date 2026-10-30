package app.osprey.media

private const val QUARTER_TURNS_IN_FULL = 4
private const val DEGREES_PER_TURN = 90
private const val IROT_ANGLE_MASK = 0x03
private const val TYPE_AT = 4
private const val TYPE_SIZE = 4

/**
 * Turns a HEIC by changing the `irot` property of its primary item and the Exif Orientation that goes with it (#71).
 * `irot` counts anticlockwise quarter turns and is what Osprey, ffmpeg and the Android platform follow; Samsung
 * Gallery follows the Exif value instead, so both are written together and kept consistent, as a camera writes them.
 */
object HeicTurn {

    const val NOT_A_HEIC = "This file is not a valid HEIC photo"
    const val DAMAGED = "This HEIC is damaged, so it was left as it is"
    const val MIRRORED = "This HEIC is mirrored, which cannot be saved turned"
    const val NO_ROTATION = "This HEIC has no rotation property to change"
    const val EXIF_NOTE =
        "Saved. Apps that read the photo's Exif, such as Samsung Gallery, may still show it unturned"

    /** [file] turned [quarterTurns] clockwise (negative: counterclockwise): the edits to make in place, or why not. */
    fun turn(file: ByteArray, quarterTurns: Int): HeicTurnResult = try {
        turned(file, quarterTurns)
    } catch (refusal: HeicRefusal) {
        HeicTurnResult.Refused(refusal.reason)
    }

    private fun turned(file: ByteArray, quarterTurns: Int): HeicTurnResult {
        val layout = layoutOf(file)
        if (layout.mirrored) refuseHeic(MIRRORED)
        val turns = quarterTurns.mod(QUARTER_TURNS_IN_FULL)
        if (turns == 0) return HeicTurnResult.Unchanged
        val irotAt = layout.irotAt ?: refuseHeic(NO_ROTATION)
        val angle = ((file.u8(irotAt) and IROT_ANGLE_MASK) - turns).mod(QUARTER_TURNS_IN_FULL)
        val edits = listOfNotNull(irotEdit(file, irotAt, angle), exifEdit(layout.exif, angle))
        val note = if (layout.exif == ExifTarget.NoOrientation) EXIF_NOTE else null
        return HeicTurnResult.Patched(edits, note)
    }

    private fun layoutOf(file: ByteArray): HeicLayout {
        val isHeif = file.size >= TYPE_AT + TYPE_SIZE &&
            String(file, TYPE_AT, TYPE_SIZE, Charsets.ISO_8859_1) == "ftyp"
        if (!isHeif) refuseHeic(NOT_A_HEIC)
        val meta = boxesIn(file, 0, file.size).firstOrNull { it.type == "meta" } ?: refuseHeic(NOT_A_HEIC)
        return HeicLayout.read(file, meta)
    }

    private fun irotEdit(file: ByteArray, at: Int, angle: Int): HeicEdit {
        val value = ((file.u8(at) and IROT_ANGLE_MASK.inv()) or angle).toByte()
        return HeicEdit(at, byteArrayOf(value))
    }

    private fun exifEdit(exif: ExifTarget, angle: Int): HeicEdit? {
        if (exif !is ExifTarget.At) return null
        val clockwise = (QUARTER_TURNS_IN_FULL - angle) % QUARTER_TURNS_IN_FULL * DEGREES_PER_TURN
        val orientation = ExifOrientation.fromClockwiseDegrees(clockwise)
        return HeicEdit(exif.offset, TiffOrientation.valueBytes(orientation, exif.bigEndian))
    }
}
