package app.osprey.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import app.osprey.filesystem.FileHandle
import app.osprey.viewer.ViewTurns
import app.osprey.viewer.saveTurnHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the viewer's Save turn button needs (#61, #74): whether it works now, whether the file is being written, the
 * line under it, what pressing it does.
 */
class TurnSaveControls(val canSave: Boolean, val saving: Boolean, val note: String?, val onSave: () -> Unit)

/**
 * Saves [handle]'s [pending] turn into its file off the UI thread. The button is off while the file is written, and
 * for a photo that cannot be saved turned the note says why. What a save said (a refusal, a failure, a note about a
 * good save) stays under the button until the user turns again, and what is running or was said belongs to
 * [viewTurns], not to this view: closing the viewer neither stops a save nor loses its outcome.
 */
@Composable
fun rememberTurnSaveControls(viewTurns: ViewTurns, handle: FileHandle, name: String, pending: Int): TurnSaveControls {
    val saving = handle in viewTurns.savingNow.collectAsState().value
    val message = viewTurns.saveMessages.collectAsState().value[handle]
    val scope = rememberCoroutineScope()
    val hint = saveTurnHint(name, pending)
    return TurnSaveControls(
        canSave = pending != 0 && hint == null && !saving,
        saving = saving,
        note = hint ?: message,
        onSave = { scope.launch { withContext(Dispatchers.IO) { viewTurns.save(handle, name) } } },
    )
}
