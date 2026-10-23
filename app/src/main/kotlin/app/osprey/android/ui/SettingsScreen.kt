package app.osprey.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.android.AndroidAppState
import app.osprey.android.SettingsState
import app.osprey.android.folderSetting
import app.osprey.android.rememberFolderPicker
import app.osprey.settings.FolderSetting
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode

private const val MODE_HELP =
    "Copy keeps the original in the input folder. Move removes it once the copy is verified. " +
        "Files already in the output are skipped by content, so Copy never imports the same file twice."

/**
 * Output media folder and the list of input media folders, each with its Move / Copy switch. Touch
 * has no hover, so the Copy explanation sits behind an info button instead of a tooltip. Fills the
 * theme background itself: it is the first screen of a fresh install, before any Scaffold exists.
 */
@Composable
fun SettingsScreen(appState: AndroidAppState) {
    val state = appState.settingsState
    val context = LocalContext.current
    val pickOutput = rememberFolderPicker { state.chooseOutput(folderSetting(context, it)) }
    val pickInput = rememberFolderPicker { state.addInput(folderSetting(context, it)) }
    var showHelp by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            OutputBlock(state, pickOutput)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            InputsBlock(state, pickInput) { showHelp = true }
            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.settings.isConfigured) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = { state.close() }) { Text("Done") }
            }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Move or Copy") },
            text = { Text(MODE_HELP) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun OutputBlock(state: SettingsState, pickOutput: () -> Unit) {
    Text("Output media folder", fontWeight = FontWeight.SemiBold)
    Text("Where the year folders and the Trash live. Every action works here.", fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FolderLabel(state.settings.output, Modifier.weight(1f))
        Button(onClick = pickOutput) {
            Text(if (state.settings.isConfigured) "Change" else "Choose folder")
        }
    }
}

@Composable
private fun InputsBlock(state: SettingsState, pickInput: () -> Unit, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Input media folders", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onHelp) { Text("ⓘ") }
    }
    Text("Folders with photos and videos to gather into the output.", fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    if (state.settings.inputs.isEmpty()) {
        Text("None yet.", fontSize = 12.sp)
    }
    state.settings.inputs.forEach { InputRow(state, it) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = pickInput) { Text("Add folder") }
}

@Composable
private fun InputRow(state: SettingsState, input: InputFolderSetting) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FolderLabel(input.folder, Modifier.weight(1f))
        Text(if (input.mode == InputMode.MOVE) "Move" else "Copy")
        Switch(
            checked = input.mode == InputMode.MOVE,
            onCheckedChange = { move ->
                val mode = if (move) InputMode.MOVE else InputMode.COPY
                state.setInputMode(input.folder.reference, mode)
            },
        )
        IconButton(onClick = { state.removeInput(input.folder.reference) }) { Text("✕") }
    }
}

/** A folder's name with where it sits underneath: a tree Uri is not readable on its own. */
@Composable
private fun FolderLabel(folder: FolderSetting?, modifier: Modifier) {
    Column(modifier) {
        if (folder == null) {
            Text("Not set yet")
            return@Column
        }
        Text(folder.displayName)
        val path = folder.location.segments.joinToString("/")
        Text("${folder.location.volume}:/$path", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
