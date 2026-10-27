package app.osprey.viewer

import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val QUARTER_TURNS_IN_FULL = 4

/**
 * The quarter turns the user gave each photo this session, kept in memory only (#51): the cards and the viewer both
 * read it, so a thumbnail follows the turn made in the viewer. A photo that is not turned has no entry.
 */
class ViewTurns {

    private val state = MutableStateFlow<Map<FileHandle, Int>>(emptyMap())

    val turns: StateFlow<Map<FileHandle, Int>> = state.asStateFlow()

    fun turnsOf(handle: FileHandle): Int = state.value[handle] ?: 0

    /** Adds [quarterTurns] (negative turns left) to [handle]'s turn. */
    fun turn(handle: FileHandle, quarterTurns: Int) {
        val total = (turnsOf(handle) + quarterTurns).mod(QUARTER_TURNS_IN_FULL)
        state.value = if (total == 0) state.value - handle else state.value + (handle to total)
    }
}
