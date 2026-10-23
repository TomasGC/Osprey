package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable state for one simple-action screen's scan/apply pane. */
class ActionUiState {
    var summary by mutableStateOf("")
    var lines by mutableStateOf(emptyList<String>())
    var applyEnabled by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var appliedSuffix by mutableStateOf("")

    /** Share of the files handled so far (0..1) during a run that reports it; null shows an indeterminate bar. */
    var progress by mutableStateOf<Float?>(null)
    var progressText by mutableStateOf("")

    /** Whether the running work can be asked to stop (Reorg only). */
    var canCancel by mutableStateOf(false)
}
