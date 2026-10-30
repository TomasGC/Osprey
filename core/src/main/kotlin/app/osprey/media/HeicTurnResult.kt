package app.osprey.media

/** One change to make in place in a HEIC: [bytes] written at [offset] of the file (#71). */
class HeicEdit(val offset: Int, val bytes: ByteArray)

/** What turning a HEIC came to (#71). */
sealed interface HeicTurnResult {
    /** The turn changes nothing in the file, so there is nothing to write. */
    data object Unchanged : HeicTurnResult

    /** The turn is these in-place edits; [note] says something the user should know about the result, if anything. */
    class Patched(val edits: List<HeicEdit>, val note: String?) : HeicTurnResult

    /** The file cannot be turned this way, for a reason the user can be told; nothing was written. */
    data class Refused(val reason: String) : HeicTurnResult
}
