package app.osprey.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.trash.TrashEntry
import app.osprey.android.AndroidAppState
import app.osprey.android.TrashController
import app.osprey.filesystem.FileHandle

/**
 * The Trash (#41): year tabs, action sub-tabs, the files of the open tab as cards to tick, and one
 * Delete and one Restore for the whole selection. Nothing is deleted before the question is answered.
 */
@Composable
fun TrashScreen(state: AndroidAppState, root: FileHandle) {
    val controller = state.trash
    var preview by remember { mutableStateOf<TrashEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TrashHeader(controller, onDelete = controller::requestDelete) { controller.restore(root) }
        Spacer(Modifier.height(12.dp))
        if (controller.contents.entries.isEmpty()) {
            Text("The Trash is empty.")
        } else {
            TrashTabs(controller)
            Spacer(Modifier.height(10.dp))
            TrashGrid(state, controller, onOpen = { preview = it })
        }
        if (controller.report.isNotEmpty()) {
            Text(controller.report.joinToString("\n"), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }

    TrashDialogs(state, root, controller)
    preview?.let { entry ->
        MediaPreview(
            mediaMetadataReader = state.mediaMetadataReader,
            handle = entry.handle,
            name = entry.name,
            checked = entry in controller.selection,
            checkLabel = "Select",
            onCheckedChange = { controller.toggle(entry) },
            onClose = { preview = null },
        )
    }
}

@Composable
private fun TrashHeader(controller: TrashController, onDelete: () -> Unit, onRestore: () -> Unit) {
    val selected = controller.selection.labels.size
    val idle = !controller.state.busy
    Text(if (selected == 0) "Nothing selected" else "$selected selected", fontSize = 13.sp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Button(onClick = onRestore, enabled = selected > 0 && idle) { Text("Restore") }
        Button(
            onClick = onDelete,
            enabled = selected > 0 && idle,
            colors = ButtonDefaults.buttonColors(containerColor = DANGER),
        ) {
            Text("🗑 Delete")
        }
    }
}

@Composable
private fun TrashTabs(controller: TrashController) {
    val years = controller.contents.years
    val year = controller.year ?: return
    ScrollableTabRow(selectedTabIndex = years.indexOf(year).coerceAtLeast(0), edgePadding = 0.dp) {
        years.forEach { candidate ->
            val count = controller.contents.entries.count { it.year == candidate }
            Tab(
                selected = candidate == year,
                onClick = { controller.selectYear(candidate) },
                text = { Text("$candidate ($count)") },
            )
        }
    }
    val actions = controller.contents.actions(year)
    TabRow(selectedTabIndex = actions.indexOf(controller.action).coerceAtLeast(0)) {
        actions.forEach { candidate ->
            Tab(
                selected = candidate == controller.action,
                onClick = { controller.action = candidate },
                text = { Text("${candidate.folderName} (${controller.contents.entries(year, candidate).size})") },
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp).clickable(onClick = controller::toggleTab),
    ) {
        Checkbox(checked = controller.tabFullySelected, onCheckedChange = { controller.toggleTab() })
        Text("Select all in this tab")
    }
}

@Composable
private fun TrashGrid(state: AndroidAppState, controller: TrashController, onOpen: (TrashEntry) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(controller.currentTab, key = { it.label }) { entry ->
            MediaCard(
                mediaMetadataReader = state.mediaMetadataReader,
                handle = entry.handle,
                name = entry.name,
                sizeBytes = entry.sizeBytes,
                checked = entry in controller.selection,
                onToggle = { controller.toggle(entry) },
                onOpen = { onOpen(entry) },
            )
        }
    }
}
