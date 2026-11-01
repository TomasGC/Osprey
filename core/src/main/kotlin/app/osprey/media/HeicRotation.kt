package app.osprey.media

/** What a decoder has to turn a HEIC by to show it upright (#76). */
object HeicRotation {

    /**
     * The `irot` of [file]'s primary item in anticlockwise quarter turns (0 to 3).
     * 0 when it has none or cannot be read.
     */
    @Suppress("SwallowedException") // a file that cannot be read is shown as stored
    fun of(file: ByteArray): Int = try {
        HeicLayout.of(file).irotAt?.let { file.u8(it) and IROT_ANGLE_MASK } ?: 0
    } catch (ignored: HeicRefusal) {
        0
    }
}
