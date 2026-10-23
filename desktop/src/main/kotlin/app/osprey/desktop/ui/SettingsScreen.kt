package app.osprey.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.desktop.AppState
import app.osprey.desktop.SettingsState
import app.osprey.desktop.pickFolder
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode

private const val OUTPUT_TITLE = "Choose the output media folder"
private const val INPUT_TITLE = "Choose a folder with photos and videos"
private const val MODE_TOOLTIP =
    "Copy keeps the original in this folder. Move removes it once the copy is verified. " +
        "Files already in the output are skipped by content, so Copy never imports the same file twice."

/** Output media folder and the list of input media folders, each with its Move / Copy switch. */
@Composable
fun SettingsScreen(appState: AppState) {
    val state = appState.settingsState
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutputBlock(state)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        InputsBlock(state)
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

@Composable
private fun OutputBlock(state: SettingsState) {
    Text("Output media folder", fontWeight = FontWeight.SemiBold)
    Text("Where the year folders and the Trash live. Every action works here.", fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.settings.output?.reference ?: "Not set yet", modifier = Modifier.weight(1f))
        Button(onClick = { pickFolder(OUTPUT_TITLE)?.let { state.chooseOutput(it) } }) {
            Text(if (state.settings.isConfigured) "Change" else "Choose folder")
        }
    }
}

@Composable
private fun InputsBlock(state: SettingsState) {
    Text("Input media folders", fontWeight = FontWeight.SemiBold)
    Text("Folders with photos and videos to gather into the output.", fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    if (state.settings.inputs.isEmpty()) {
        Text("None yet.", fontSize = 12.sp)
    }
    state.settings.inputs.forEach { InputRow(state, it) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { pickFolder(INPUT_TITLE)?.let { state.addInput(it) } }) {
        Text("Add folder")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputRow(state: SettingsState, input: InputFolderSetting) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(input.folder.reference, modifier = Modifier.weight(1f))
        TooltipArea(
            tooltip = {
                Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp) {
                    Text(MODE_TOOLTIP, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (input.mode == InputMode.MOVE) "Move" else "Copy")
                Switch(
                    checked = input.mode == InputMode.MOVE,
                    onCheckedChange = { move ->
                        val mode = if (move) InputMode.MOVE else InputMode.COPY
                        state.setInputMode(input.folder.reference, mode)
                    },
                )
            }
        }
        IconButton(onClick = { state.removeInput(input.folder.reference) }) { Text("✕") }
    }
}
