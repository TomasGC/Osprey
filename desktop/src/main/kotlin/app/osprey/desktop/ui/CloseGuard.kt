package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.osprey.jobs.JobQueue
import app.osprey.jobs.closeRisk
import kotlinx.coroutines.flow.first

private const val RISK_TEXT = "Quitting now cuts it. Files stay safe: a stopped run leaves the rest untouched, " +
    "and the next scan finds what is left."
private const val STOPPING_TEXT = "Stopping after the file it is on..."

/** Asked when the window is closed while a job that changes files is queued or running. */
@Composable
fun CloseGuard(jobs: JobQueue, onKeep: () -> Unit, onQuit: () -> Unit) {
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(stopping) {
        if (stopping) {
            jobs.entries.value.forEach { jobs.stop(it.id) }
            jobs.entries.first { !closeRisk(it) }
            onQuit()
        } else {
            // The job ended on its own while the question was open: nothing is at risk, so the question goes away.
            jobs.entries.first { !closeRisk(it) }
            onKeep()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!stopping) onKeep() },
        title = { Text("A job is still running") },
        text = { Text(if (stopping) STOPPING_TEXT else RISK_TEXT) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { stopping = true }, enabled = !stopping) { Text("Stop and quit") }
                OutlinedButton(onClick = onQuit) { Text("Quit now") }
            }
        },
        dismissButton = { TextButton(onClick = onKeep, enabled = !stopping) { Text("Keep running") } },
    )
}
