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

    companion object {
        /** The orientation for an EXIF value 1-8; a missing or out of range value means the photo is upright. */
        fun fromExif(value: Int?): ExifOrientation = entries.getOrNull((value ?: 1) - 1) ?: NORMAL
    }
}
