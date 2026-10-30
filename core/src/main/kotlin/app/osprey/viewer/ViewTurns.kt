package app.osprey.viewer

import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val QUARTER_TURNS_IN_FULL = 4

/**
 * The quarter turns the user gave each photo this session, kept in memory only (#51): the cards and the viewer both
 * read it, so a thumbnail follows the turn made in the viewer. A photo that is not turned has no entry.
 */
class ViewTurns(
    private val saveTurn: (FileHandle, String, Int) -> TurnSaveResult = { _, _, _ ->
        TurnSaveResult.Refused(NO_SAVER)
    },
) {

    private val state = MutableStateFlow<Map<FileHandle, Int>>(emptyMap())

    val turns: StateFlow<Map<FileHandle, Int>> = state.asStateFlow()

    private val revisionState = MutableStateFlow<Map<FileHandle, Int>>(emptyMap())

    /** How many times each photo has been saved this session: a view reads the photo again when its number changes. */
    val revisions: StateFlow<Map<FileHandle, Int>> = revisionState.asStateFlow()

    fun revisionOf(handle: FileHandle): Int = revisionState.value[handle] ?: 0

    fun turnsOf(handle: FileHandle): Int = state.value[handle] ?: 0

    /** Adds [quarterTurns] (negative turns left) to [handle]'s turn. */
    fun turn(handle: FileHandle, quarterTurns: Int) = state.update { current ->
        val total = ((current[handle] ?: 0) + quarterTurns).mod(QUARTER_TURNS_IN_FULL)
        if (total == 0) current - handle else current + (handle to total)
    }

    /**
     * Writes [handle]'s pending turn into its file (#61). On success exactly the turns that were written are taken off
     * (one made while the file was being written stays) and the photo's revision goes up so every view reads it
     * again; on a refusal or a failure nothing changes. A photo with no pending turn asks nothing.
     */
    fun save(handle: FileHandle, name: String): TurnSaveResult {
        val pending = turnsOf(handle)
        if (pending == 0) return TurnSaveResult.Saved
        val result = saveTurn(handle, name, pending)
        if (result.isSaved) {
            revisionState.update { it + (handle to ((it[handle] ?: 0) + 1)) }
            turn(handle, -pending)
        }
        return result
    }

    companion object {
        const val NO_SAVER = "Saving a turn is not available here"
    }
}
