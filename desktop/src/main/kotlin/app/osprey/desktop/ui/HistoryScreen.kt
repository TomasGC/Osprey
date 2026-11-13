package app.osprey.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.history.FileRecord
import app.osprey.history.HistoryController
import app.osprey.history.HistoryView
import app.osprey.history.RunRecord
import app.osprey.history.RunSection
import app.osprey.history.UNDO_WINDOW_MS
import app.osprey.history.fileChangeLines
import app.osprey.history.fileLine
import app.osprey.history.runCounts
import app.osprey.history.runDurationText
import app.osprey.history.runFileGroups
import app.osprey.history.runSubtitle
import app.osprey.history.runWhenText
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * The History screen (#79, #110): runs by state, a run's page, and the clear button, which removes the checked runs or
 * else the successful ones. A removal shows a notice with Undo and the store forgets the runs when it ends.
 */
@Composable
fun HistoryScreen(controller: HistoryController, zone: ZoneId = ZoneId.systemDefault()) {
    val view by controller.view.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(controller) { controller.follow() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(view.removalId) {
        if (view.removed > 0) {
            delay(UNDO_WINDOW_MS)
            controller.commit()
        }
    }
    DisposableEffect(controller) { onDispose { controller.commit() } }

    val run = view.selected
    Box(
        modifier = Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent {
            val delete = it.type == KeyEventType.KeyDown && it.key == Key.Delete && view.selecting
            if (delete) controller.clear()
            delete
        },
    ) {
        if (run != null) {
            RunPage(run, zone, onBack = controller::back)
        } else {
            HistoryList(view, zone, controller)
        }
        view.removedText?.let {
            val position = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            RemovalNotice(it, controller::undo, controller::commit, position)
        }
    }
}

@Composable
private fun HistoryList(view: HistoryView, zone: ZoneId, controller: HistoryController) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = controller::clear, enabled = view.clearEnabled) { Text(view.clearLabel) }
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
                    items(section.runs, key = { it.id }) { run ->
                        RunRow(run, run.id in view.checked, zone, controller)
                    }
                }
            }
        }
    }
}

/** A run: a checkbox column (empty for a run that cannot be checked) and its card. */
@Composable
private fun RunRow(run: RunRecord, checked: Boolean, zone: ZoneId, controller: HistoryController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(40.dp)) {
            if (!run.state.isActive) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { controller.longPress(run.id) },
                    modifier = Modifier.semantics { contentDescription = "Select ${run.action}" },
                )
            }
        }
        RunCard(run, zone, Modifier.weight(1f)) { controller.press(run.id) }
    }
}

@Composable
private fun RunCard(run: RunRecord, zone: ZoneId, modifier: Modifier, onPress: () -> Unit) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onPress)) {
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

/** What a removal says, with the way back (Undo) and a way to make it final at once (the close button). */
@Composable
private fun RemovalNotice(text: String, onUndo: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val onNotice = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Text(text)
            TextButton(onClick = onUndo, colors = onNotice) { Text("Undo") }
            TextButton(
                onClick = onClose,
                colors = onNotice,
                modifier = Modifier.semantics { contentDescription = "Close notice" },
            ) { Text("✕") }
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
