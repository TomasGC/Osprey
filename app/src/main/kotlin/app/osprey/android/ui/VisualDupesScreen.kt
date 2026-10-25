package app.osprey.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.android.AndroidAppState
import app.osprey.android.VisualDupeGroupUi
import app.osprey.filesystem.FileHandle

/**
 * The POC's Visual Dupes tab: scan (can take a minute or two), stats chips, a card grid per
 * group, and a fullscreen preview -- the one action that stays review-before-apply, never
 * auto-applied, since perceptual similarity can still be wrong.
 */
@Composable
fun VisualDupesScreen(state: AndroidAppState, yearDir: FileHandle) {
    val controller = state.visual
    val groups = controller.groupsIn(yearDir)
    var preview by remember { mutableStateOf<Pair<VisualDupeGroupUi, VisualDupeMember>?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { controller.onScan(yearDir) }, enabled = !controller.scanning) {
                Text(if (controller.scanning) "Scanning..." else "Scan (can take a minute or two)")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (groups.isNotEmpty()) {
            VisualDupeStatsRow(groups)
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(groups, key = { it.index }) { group ->
                VisualDupeGroupCard(state, yearDir, group, onPreview = { member -> preview = group to member })
            }
        }
    }

    preview?.let { (group, member) ->
        MediaPreview(
            mediaMetadataReader = state.mediaMetadataReader,
            handle = member.handle,
            name = member.name,
            checked = group.keep[member.name] == true,
            checkLabel = "Keep this one",
            onCheckedChange = { group.keep[member.name] = it },
            onClose = { preview = null },
        )
    }
}
