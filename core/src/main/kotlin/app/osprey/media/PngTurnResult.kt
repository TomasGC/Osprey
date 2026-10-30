package app.osprey.media

/** What turning a PNG's pixels came to (#70). */
sealed interface PngTurnResult {
    /** The whole file with its pixels turned. */
    class Turned(val bytes: ByteArray) : PngTurnResult

    /** The turn changes nothing in the file, so there is nothing to write. */
    data object Unchanged : PngTurnResult

    /** The file cannot be turned this way, for a reason the user can be told; nothing was written. */
    data class Refused(val reason: String) : PngTurnResult
}
