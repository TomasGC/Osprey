package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable state for one simple-action tab's scan/apply pane. */
class ActionUiState {
    var summary by mutableStateOf("")
    var lines by mutableStateOf(emptyList<String>())
    var applyEnabled by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var appliedSuffix by mutableStateOf("")
}
