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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.actions.visual.VisualDupePlanner
import app.osprey.android.AndroidAppState
import app.osprey.android.VisualDupeGroupUi
import app.osprey.android.toUiGroups
import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scan (can take a minute or two), stats chips, a card grid per group, and a fullscreen preview
 * -- same screen as desktop's (#12), single-column phone layout. The one action that stays
 * review-before-apply, never auto-applied, since perceptual similarity can still be wrong.
 */
@Composable
fun VisualDupesScreen(state: AndroidAppState, yearDir: FileHandle) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<VisualDupeGroupUi>>(emptyList()) }
    var preview by remember { mutableStateOf<Pair<VisualDupeGroupUi, VisualDupeMember>?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(
                onClick = {
                    scanning = true
                    scope.launch {
                        val plan = withContext(Dispatchers.IO) {
                            VisualDupePlanner(state.fs, state.mediaMetadataReader).plan(yearDir)
                        }
                        groups = toUiGroups(plan.groups)
                        scanning = false
                    }
                },
                enabled = !scanning,
            ) {
                Text(if (scanning) "Scanning..." else "Scan (can take a minute or two)")
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
        PreviewLightbox(group, member, state.mediaMetadataReader, onClose = { preview = null })
    }
}
