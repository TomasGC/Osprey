package app.osprey.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.trash.ConflictChoice
import app.osprey.actions.trash.RestoreConflict
import app.osprey.android.AndroidAppState
import app.osprey.android.TrashController
import app.osprey.filesystem.FileHandle

/** The two questions the Trash can ask: delete for good, and which copy to keep when a restore finds a clash. */
@Composable
fun TrashDialogs(state: AndroidAppState, root: FileHandle, controller: TrashController) {
    controller.confirmation?.let { question ->
        AlertDialog(
            onDismissRequest = { controller.confirmation = null },
            title = { Text("Delete for good?") },
            text = { Text(question) },
            confirmButton = {
                Button(
                    onClick = { controller.confirmDelete(root) },
                    colors = ButtonDefaults.buttonColors(containerColor = DANGER),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { controller.confirmation = null }) { Text("Cancel") } },
        )
    }
    controller.conflicts.firstOrNull()?.let { conflict ->
        RestoreConflictDialog(state, conflict, remaining = controller.conflicts.size) { choice, all ->
            if (all) controller.resolveAll(root, choice) else controller.resolve(root, conflict, choice)
            state.refreshYears()
        }
    }
}

/** One clash, Windows style: the Trash's file next to the library's, and which of them to keep. */
@Composable
private fun RestoreConflictDialog(
    state: AndroidAppState,
    conflict: RestoreConflict,
    remaining: Int,
    onChoose: (ConflictChoice, Boolean) -> Unit,
) {
    var applyToAll by remember(conflict) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${conflict.entry.name} already exists in ${conflict.entry.year}") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SideLabelled("From the Trash") {
                        ShownCard(state, conflict.entry.handle, conflict.entry.name, conflict.entry.sizeBytes)
                    }
                    SideLabelled("In the library") {
                        ShownCard(state, conflict.existing.handle, conflict.existing.name, conflict.existing.sizeBytes)
                    }
                }
                if (remaining > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                        Text("Do this for the $remaining conflicts", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onChoose(ConflictChoice.TRASH_COPY, applyToAll) }) { Text("Keep Trash copy") }
                Button(onClick = { onChoose(ConflictChoice.LIBRARY_COPY, applyToAll) }) { Text("Keep library copy") }
                OutlinedButton(onClick = { onChoose(ConflictChoice.BOTH, applyToAll) }) { Text("Keep both") }
            }
        },
    )
}

@Composable
private fun SideLabelled(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** A card that only shows a file: no toggle, nothing opens. */
@Composable
private fun ShownCard(state: AndroidAppState, handle: FileHandle, name: String, sizeBytes: Long) {
    MediaCard(
        state.mediaMetadataReader, state.viewTurns, handle, name, sizeBytes,
        checked = false, onToggle = {}, onOpen = {}, showToggle = false,
    )
}
