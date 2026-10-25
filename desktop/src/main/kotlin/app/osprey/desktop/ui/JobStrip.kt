package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.jobs.JobEntry
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import app.osprey.jobs.jobBody
import app.osprey.jobs.jobTitle

/** Every job that is waiting, running or just over, one row each, with Stop on the ones that can still stop. */
@Composable
fun JobStrip(jobs: JobQueue) {
    val entries by jobs.entries.collectAsState()
    if (entries.isEmpty()) return
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                JobRow(entry, onStop = { jobs.stop(entry.id) }, onDismiss = { jobs.dismiss(entry.id) })
            }
        }
    }
}

@Composable
private fun JobRow(entry: JobEntry, onStop: () -> Unit, onDismiss: () -> Unit) {
    val state = entry.state
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(jobTitle(entry, waiting = 0), fontSize = 13.sp)
            Text(jobBody(state), fontSize = 12.sp)
            if (state is JobState.Running) {
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (state is JobState.Finished) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        } else {
            OutlinedButton(onClick = onStop, enabled = !(state is JobState.Running && state.stopping)) { Text("Stop") }
        }
    }
}
