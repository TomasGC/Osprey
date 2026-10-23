package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.desktop.ActionUiState
import kotlinx.coroutines.launch

/**
 * The scan/apply layout shared by all five simple-action tabs: a Scan button, an Apply button
 * (disabled until a scan produced a non-empty plan), a summary line, and a scrollable log pane
 * -- same shape as the POC's `.scan-row` + `pre.loglines`.
 */
@Composable
fun ActionPane(
    state: ActionUiState,
    onScan: suspend () -> Unit,
    onApply: suspend () -> Unit,
    scanLabel: String = "Scan",
    onCancel: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { scope.launch { onScan() } }, enabled = !state.busy) {
                Text(scanLabel)
            }
            Button(onClick = { scope.launch { onApply() } }, enabled = state.applyEnabled && !state.busy) {
                Text(if (state.busy) "Working..." else "Apply")
            }
            if (onCancel != null && state.canCancel) {
                OutlinedButton(onClick = onCancel) { Text("Stop") }
            }
            Text(state.summary + state.appliedSuffix, fontSize = 14.sp)
        }
        if (state.busy) {
            ProgressBlock(state)
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
private fun ProgressBlock(state: ActionUiState) {
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
