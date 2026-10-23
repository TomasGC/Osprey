package app.osprey.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.android.ActionUiState
import kotlinx.coroutines.launch

/**
 * The scan/apply layout shared by every simple-action screen, single-column for phone width:
 * a Scan/Apply button row, a summary line, an optional limitation banner (Dates, see
 * [applyBlockedReason]), and a scrollable log pane -- same contract as desktop's `ActionPane`
 * (#11), minus the sidebar it doesn't have room for.
 */
@Composable
fun ActionPane(
    state: ActionUiState,
    onScan: suspend () -> Unit,
    onApply: suspend () -> Unit,
    scanLabel: String = "Scan",
    applyBlockedReason: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { scope.launch { onScan() } }, enabled = !state.busy) {
                Text(scanLabel)
            }
            Button(
                onClick = { scope.launch { onApply() } },
                enabled = state.applyEnabled && !state.busy && applyBlockedReason == null,
            ) {
                Text(if (state.busy) "Working..." else "Apply")
            }
            if (onCancel != null && state.canCancel) {
                OutlinedButton(onClick = onCancel) { Text("Stop") }
            }
        }
        if (state.busy) {
            ProgressBlock(state)
        }
        Spacer(Modifier.height(8.dp))
        Text(state.summary + state.appliedSuffix, fontSize = 14.sp)
        if (applyBlockedReason != null) {
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                Text(
                    applyBlockedReason,
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(state.lines.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

/** A bar under the buttons while work runs: filled as files are handled, indeterminate while scanning. */
@Composable
fun ProgressBlock(state: ActionUiState) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        if (state.progressText.isNotEmpty()) Text(state.progressText, fontSize = 12.sp)
    }
}
