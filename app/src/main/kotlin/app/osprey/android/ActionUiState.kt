package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable state for one action tab's scan/apply pane. Progress and Stop belong to the job, not to the tab. */
class ActionUiState {
    var summary by mutableStateOf("")
    var lines by mutableStateOf(emptyList<String>())
    var applyEnabled by mutableStateOf(false)

    /** True while a job of this action is waiting or running: its buttons are off, a second one is not queued. */
    var busy by mutableStateOf(false)
    var appliedSuffix by mutableStateOf("")
}
