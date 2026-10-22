package app.osprey.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.actions.visual.moveUncheckedToTodeleteDups
import app.osprey.desktop.AppState
import app.osprey.desktop.VisualDupeGroupUi
import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("MagicNumber") // Hex color literals, not measurements.
private val BURST_BORDER = Color(0xFF5A4A12)

@Suppress("MagicNumber")
private val BURST_BG = Color(0xFF1C1A14)

@Suppress("MagicNumber")
private val BURST_BADGE = Color(0xFFE8B400)

@Suppress("MagicNumber")
private val BURST_BADGE_TEXT = Color(0xFF1A1500)

@Suppress("MagicNumber")
private val DANGER = Color(0xFFAA3333)
private const val DONE_ALPHA = 0.4f

/** One group: index badge, burst badge or plain count, status, "Move unchecked", and the member grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualDupeGroupCard(
    state: AppState,
    yearDir: FileHandle,
    group: VisualDupeGroupUi,
    onPreview: (VisualDupeMember) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (group.isBurst) BURST_BG else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (group.isBurst) BURST_BORDER else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().alpha(if (group.done) DONE_ALPHA else 1f),
    ) {
        Column(Modifier.padding(16.dp)) {
            GroupHeader(state, yearDir, group)
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                group.members.forEach { member ->
                    VisualDupeThumbnail(
                        mediaMetadataReader = state.mediaMetadataReader,
                        member = member,
                        kept = group.keep[member.name] == true,
                        onToggle = { group.keep[member.name] = group.keep[member.name] != true },
                        onOpen = { onPreview(member) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(state: AppState, yearDir: FileHandle, group: VisualDupeGroupUi) {
    val scope = rememberCoroutineScope()
    var moving by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IndexBadge(group.index)
        if (group.isBurst) {
            val keptCount = group.members.count { group.keep[it.name] == true }
            Surface(color = BURST_BADGE, shape = RoundedCornerShape(20.dp)) {
                Text(
                    "BURST SET — KEEP ALL $keptCount",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BURST_BADGE_TEXT,
                )
            }
        } else {
            Text(
                "${group.members.size} near-identical",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(group.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                val keptNames = group.members.filter { group.keep[it.name] == true }.map { it.name }.toSet()
                when {
                    keptNames.isEmpty() -> group.status = "Keep at least one image."
                    keptNames.size == group.members.size -> group.status = "Nothing unchecked -- nothing to move."
                    else -> {
                        moving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                moveUncheckedToTodeleteDups(state.fs, yearDir, group.members, keptNames)
                            }
                            moving = false
                            group.done = true
                            val skippedNote = if (result.skipped.isNotEmpty()) {
                                " (skipped: ${result.skipped.joinToString(", ")})"
                            } else {
                                ""
                            }
                            group.status = "Moved ${result.moved.size}$skippedNote"
                        }
                    }
                }
            },
            enabled = !moving && !group.done,
            colors = ButtonDefaults.buttonColors(containerColor = DANGER),
        ) {
            Text(if (moving) "Working..." else "Move unchecked")
        }
    }
}

@Composable
private fun IndexBadge(index: Int) {
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("#$index", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
