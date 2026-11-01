package app.osprey.media

private const val QUARTER_TURNS_IN_FULL = 4
private const val DEGREES_PER_TURN = 90
internal const val IROT_ANGLE_MASK = 0x03

/**
 * Turns a HEIC by changing the `irot` property of its primary item and the Exif Orientation that goes with it (#71).
 * `irot` counts anticlockwise quarter turns and is what Osprey, ffmpeg and the Android platform follow; Samsung
 * Gallery follows the Exif value instead, so both are written together and kept consistent, as a camera writes them.
 */
object HeicTurn {

    const val NOT_A_HEIC = "This file is not a valid HEIC photo"
    const val DAMAGED = "This HEIC is damaged, so it was left as it is"
    const val MIRRORED = "This HEIC is mirrored, which cannot be saved turned"
    const val UNSAFE_LAYOUT = "This HEIC's layout cannot be turned without moving its data, so it was left as it is"
    const val EXIF_NOTE =
        "Saved. Apps that read the photo's Exif, such as Samsung Gallery, may still show it unturned"

    /** [file] turned [quarterTurns] clockwise (negative: counterclockwise): the edits to make in place, or why not. */
    fun turn(file: ByteArray, quarterTurns: Int): HeicTurnResult = turn(file, quarterTurns, HeicRebuild::isSound)

    /** As [turn], with [isSound] as the check on a rebuilt file: a seam, so that its refusal can be tested. */
    internal fun turn(
        file: ByteArray,
        quarterTurns: Int,
        isSound: (ByteArray, ByteArray, Int) -> Boolean,
    ): HeicTurnResult = try {
        turned(file, quarterTurns, isSound)
    } catch (refusal: HeicRefusal) {
        HeicTurnResult.Refused(refusal.reason)
    }

    private fun turned(
        file: ByteArray,
        quarterTurns: Int,
        isSound: (ByteArray, ByteArray, Int) -> Boolean,
    ): HeicTurnResult {
        val layout = HeicLayout.of(file)
        if (layout.mirrored) refuseHeic(MIRRORED)
        val turns = quarterTurns.mod(QUARTER_TURNS_IN_FULL)
        if (turns == 0) return HeicTurnResult.Unchanged
        val note = if (layout.exif == ExifTarget.NoOrientation) EXIF_NOTE else null
        val irotAt = layout.irotAt ?: return rebuilt(file, layout, (-turns).mod(QUARTER_TURNS_IN_FULL), note, isSound)
        val angle = ((file.u8(irotAt) and IROT_ANGLE_MASK) - turns).mod(QUARTER_TURNS_IN_FULL)
        val edits = listOfNotNull(irotEdit(file, irotAt, angle), exifEdit(layout.exif, angle))
        return HeicTurnResult.Patched(edits, note)
    }

    /** A photo with no irot gets one, set to [angle], when that can be done without moving any data and checks out. */
    private fun rebuilt(
        file: ByteArray,
        layout: HeicLayout,
        angle: Int,
        note: String?,
        isSound: (ByteArray, ByteArray, Int) -> Boolean,
    ): HeicTurnResult {
        val bytes = HeicRebuild.insert(file, layout, angle, listOfNotNull(exifEdit(layout.exif, angle)))
        if (bytes == null || !isSound(file, bytes, angle)) refuseHeic(UNSAFE_LAYOUT)
        return HeicTurnResult.Rebuilt(bytes, note)
    }

    private fun irotEdit(file: ByteArray, at: Int, angle: Int): HeicEdit {
        val value = ((file.u8(at) and IROT_ANGLE_MASK.inv()) or angle).toByte()
        return HeicEdit(at, byteArrayOf(value))
    }

    private fun exifEdit(exif: ExifTarget, angle: Int): HeicEdit? =
        (exif as? ExifTarget.At)?.let { HeicEdit(it.offset, exifBytes(it, angle)) }

    /** The two bytes of the Exif Orientation that show the same picture as an `irot` of [angle]. */
    internal fun exifBytes(exif: ExifTarget.At, angle: Int): ByteArray {
        val clockwise = (QUARTER_TURNS_IN_FULL - angle) % QUARTER_TURNS_IN_FULL * DEGREES_PER_TURN
        return TiffOrientation.valueBytes(ExifOrientation.fromClockwiseDegrees(clockwise), exif.bigEndian)
    }
}
