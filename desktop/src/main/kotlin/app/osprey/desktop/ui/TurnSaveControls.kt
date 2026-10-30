package app.osprey.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.osprey.filesystem.FileHandle
import app.osprey.viewer.ViewTurns
import app.osprey.viewer.saveTurnHint
import app.osprey.viewer.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the viewer's Save turn button needs (#61): whether it works now, the line under it, what pressing it does. */
class TurnSaveControls(val canSave: Boolean, val note: String?, val onSave: () -> Unit)

/**
 * Saves [handle]'s [pending] turn into its file off the UI thread. The button is off while the file is written, and
 * for a photo that cannot be saved turned the note says why; a refusal, a failure or a note about a good save stays
 * under the button until the user turns again.
 */
@Composable
fun rememberTurnSaveControls(viewTurns: ViewTurns, handle: FileHandle, name: String, pending: Int): TurnSaveControls {
    var saving by remember { mutableStateOf(false) }
    var failure by remember(handle) { mutableStateOf<String?>(null) }
    // A message stays under the button until the user turns again: a save that worked takes pending to 0.
    LaunchedEffect(handle, pending) { if (pending != 0) failure = null }
    val scope = rememberCoroutineScope()
    val hint = saveTurnHint(name, pending)
    return TurnSaveControls(
        canSave = pending != 0 && hint == null && !saving,
        note = hint ?: failure,
        onSave = {
            saving = true
            scope.launch {
                failure = withContext(Dispatchers.IO) { viewTurns.save(handle, name) }.userMessage
                saving = false
            }
        },
    )
}
