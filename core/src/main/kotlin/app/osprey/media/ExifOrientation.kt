package app.osprey.media

/** The eight EXIF orientations: how the stored pixels must be transformed to show the photo upright. */
enum class ExifOrientation(val swapsDimensions: Boolean) {
    NORMAL(false),
    FLIP_HORIZONTAL(false),
    ROTATE_180(false),
    FLIP_VERTICAL(false),
    TRANSPOSE(true),
    ROTATE_90(true),
    TRANSVERSE(true),
    ROTATE_270(true),
    ;

    /**
     * The orientation to store once the photo, shown upright by this one, is turned [quarterTurns] clockwise
     * (negative turns counterclockwise), so a saved turn changes the tag and no pixel (#61).
     */
    fun turnedBy(quarterTurns: Int): ExifOrientation =
        (0 until quarterTurns.mod(QUARTER_TURNS_IN_FULL)).fold(this) { current, _ -> CLOCKWISE.getValue(current) }

    companion object {
        private const val QUARTER_TURNS_IN_FULL = 4

        /** The EXIF table for one clockwise quarter turn: values 1 to 8 become 6, 7, 8, 5, 2, 3, 4, 1. */
        private val CLOCKWISE = mapOf(
            NORMAL to ROTATE_90, FLIP_HORIZONTAL to TRANSVERSE, ROTATE_180 to ROTATE_270, FLIP_VERTICAL to TRANSPOSE,
            TRANSPOSE to FLIP_HORIZONTAL, ROTATE_90 to ROTATE_180, TRANSVERSE to FLIP_VERTICAL, ROTATE_270 to NORMAL,
        )

        /** The orientation for an EXIF value 1-8; a missing or out of range value means the photo is upright. */
        fun fromExif(value: Int?): ExifOrientation = entries.getOrNull((value ?: 1) - 1) ?: NORMAL

        /** The orientation for a HEIC rotation in degrees clockwise (90, 180, 270); anything else is upright. */
        fun fromClockwiseDegrees(degrees: Int?): ExifOrientation = when (degrees) {
            QUARTER_TURN -> ROTATE_90
            HALF_TURN -> ROTATE_180
            THREE_QUARTER_TURN -> ROTATE_270
            else -> NORMAL
        }

        private const val QUARTER_TURN = 90
        private const val HALF_TURN = 180
        private const val THREE_QUARTER_TURN = 270
    }
}
