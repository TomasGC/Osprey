package app.osprey.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.history.FileRecord
import app.osprey.history.HistoryController
import app.osprey.history.HistoryView
import app.osprey.history.RunRecord
import app.osprey.history.RunSection
import app.osprey.history.fileChangeLines
import app.osprey.history.fileLine
import app.osprey.history.runCounts
import app.osprey.history.runDurationText
import app.osprey.history.runFileGroups
import app.osprey.history.runSubtitle
import app.osprey.history.runWhenText
import java.time.ZoneId

/** The History screen (#79): runs by state, a run's page, and Clear history behind a question. */
@Composable
fun HistoryScreen(controller: HistoryController, zone: ZoneId = ZoneId.systemDefault()) {
    val view by controller.view.collectAsState()
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { controller.follow() }

    val run = view.selected
    if (run != null) {
        RunPage(run, zone, onBack = controller::back)
    } else {
        HistoryList(view, zone, onOpen = controller::open, onClear = { asking = true })
    }
    if (asking) {
        ClearHistoryDialog(
            ended = view.endedCount,
            onForget = {
                controller.clear()
                asking = false
            },
            onCancel = { asking = false },
        )
    }
}

@Composable
private fun HistoryList(view: HistoryView, zone: ZoneId, onOpen: (Long) -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = onClear, enabled = view.endedCount > 0) { Text("Clear history") }
        }
        Spacer(Modifier.height(12.dp))
        if (view.isEmpty) {
            Text("No runs yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                view.sections.forEach { section ->
                    item(key = "title-${section.state}") {
                        Text("${section.title} (${section.runs.size})", fontWeight = FontWeight.Bold)
                    }
                    items(section.runs, key = { it.id }) { run -> RunCard(run, zone) { onOpen(run.id) } }
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: RunRecord, zone: ZoneId, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(run.action, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(runWhenText(run, zone), fontSize = 12.sp)
            }
            Text(runSubtitle(run), fontSize = 13.sp)
            Text(runCounts(run), fontSize = 12.sp)
        }
    }
}

/** A run's page: what it did, its log, and every file with what changed. A long run scrolls, it never truncates. */
@Composable
private fun RunPage(run: RunRecord, zone: ZoneId, onBack: () -> Unit) {
    val whenLine = listOfNotNull(runWhenText(run, zone), runDurationText(run)?.let { "took $it" })
        .joinToString(" · ")
    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { Text(run.action, fontSize = 20.sp) }
        item { Text(RunSection(run.state, listOf(run)).title, fontSize = 13.sp) }
        item { Text(runSubtitle(run)) }
        item { Text(whenLine, fontSize = 12.sp) }
        item { Text(runCounts(run), fontSize = 12.sp) }
        if (run.log.isNotEmpty()) {
            item { Text("Log", fontWeight = FontWeight.Bold) }
            items(run.log) { Text(it, fontSize = 12.sp) }
        }
        runFileGroups(run).forEach { group ->
            item(key = "group-${group.result}") { Text(group.title, fontWeight = FontWeight.Bold) }
            items(group.files) { FileEntry(it, zone) }
        }
    }
}

@Composable
private fun FileEntry(record: FileRecord, zone: ZoneId) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(fileLine(record), fontSize = 13.sp)
        fileChangeLines(record, zone).forEach {
            Text(it, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun ClearHistoryDialog(ended: Int, onForget: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Clear history?") },
        text = {
            Text("Forget $ended ended ${if (ended == 1) "run" else "runs"}? A run that is waiting or running stays.")
        },
        confirmButton = {
            Button(onClick = onForget, colors = ButtonDefaults.buttonColors(containerColor = DANGER)) {
                Text("Forget")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
