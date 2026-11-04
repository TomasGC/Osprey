package app.osprey.viewer

import app.osprey.filesystem.FileHandle
import app.osprey.history.FileState
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.RunRecorder
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
    private val recorder: RunRecorder = RunRecorder(InMemoryJobHistoryStore()),
    private val fileState: (FileHandle, String) -> FileState? = { _, _ -> null },
) {

    private val state = MutableStateFlow<Map<FileHandle, Int>>(emptyMap())

    val turns: StateFlow<Map<FileHandle, Int>> = state.asStateFlow()

    private val revisionState = MutableStateFlow<Map<FileHandle, Int>>(emptyMap())

    /** How many times each photo has been saved this session: a view reads the photo again when its number changes. */
    val revisions: StateFlow<Map<FileHandle, Int>> = revisionState.asStateFlow()

    fun revisionOf(handle: FileHandle): Int = revisionState.value[handle] ?: 0

    fun turnsOf(handle: FileHandle): Int = state.value[handle] ?: 0

    private val savingState = MutableStateFlow<Set<FileHandle>>(emptySet())

    /** Photos whose file is being written right now (#74): the viewer shows it, and a second save is refused. */
    val savingNow: StateFlow<Set<FileHandle>> = savingState.asStateFlow()

    private val messageState = MutableStateFlow<Map<FileHandle, String>>(emptyMap())

    /**
     * What the last save of each photo said when it was not a clean success (#74): a failure, a refusal or the note of
     * a good save. It outlives the viewer, so a save that failed after Close is told when the photo is opened again,
     * and it goes when the photo is turned again or saved cleanly.
     */
    val saveMessages: StateFlow<Map<FileHandle, String>> = messageState.asStateFlow()

    /** Adds [quarterTurns] (negative turns left) to [handle]'s turn. */
    fun turn(handle: FileHandle, quarterTurns: Int) {
        state.update { current ->
            val total = ((current[handle] ?: 0) + quarterTurns).mod(QUARTER_TURNS_IN_FULL)
            if (total == 0) current - handle else current + (handle to total)
        }
        messageState.update { it - handle }
    }

    /**
     * Writes [handle]'s pending turn into its file (#61). On success exactly the turns that were written are taken off
     * (one made while the file was being written stays) and the photo's revision goes up so every view reads it
     * again; on a refusal or a failure nothing changes. A photo with no pending turn asks nothing.
     * A photo already being saved is refused, and a saver that throws is a failed save: one photo never stops the rest.
     */
    fun save(handle: FileHandle, name: String): TurnSaveResult {
        val pending = turnsOf(handle)
        if (pending == 0) return TurnSaveResult.Saved
        if (!claim(handle)) return TurnSaveResult.Refused(ALREADY_SAVING)
        val run = recorder.begin(SAVE_TURN).also { it.start() }
        try {
            val before = stateOf(handle, name)
            val result = runSaver(handle, name, pending)
            val after = if (result.isSaved) stateOf(handle, name) else null
            if (result.isSaved) {
                revisionState.update { it + (handle to ((it[handle] ?: 0) + 1)) }
                turn(handle, -pending)
            }
            result.userMessage?.let { message -> messageState.update { it + (handle to message) } }
            run.endSave(name, pending, result, before, after)
            return result
        } finally {
            savingState.update { it - handle }
        }
    }

    private fun claim(handle: FileHandle): Boolean = synchronized(savingState) {
        (handle !in savingState.value).also { free -> if (free) savingState.update { it + handle } }
    }

    /** The photo's file as it is now, for the History; a lookup that fails gives none and never breaks the save. */
    @Suppress("TooGenericExceptionCaught")
    private fun stateOf(handle: FileHandle, name: String): FileState? =
        try {
            fileState(handle, name)
        } catch (ignored: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught") // Any saver error is one photo's failure, told to the user, never a crash.
    private fun runSaver(handle: FileHandle, name: String, pending: Int): TurnSaveResult =
        try {
            saveTurn(handle, name, pending)
        } catch (e: Exception) {
            TurnSaveResult.Failed(e.message ?: e.javaClass.simpleName)
        }

    companion object {
        const val NO_SAVER = "Saving a turn is not available here"
        const val ALREADY_SAVING = "This photo is already being saved"
        const val SAVING = "Saving…"
        const val SAVE_TURN = "Save turn"
    }
}
