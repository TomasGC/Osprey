package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.desktop.ActionTab
import app.osprey.desktop.AppState

/** Library path, year picker, the action list and the Settings cog -- collapsible, like the POC's burger sidebar. */
@Composable
fun Sidebar(state: AppState) {
    val width = if (state.sidebarCollapsed) 56.dp else 230.dp
    Surface(modifier = Modifier.width(width).fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
            SidebarHead(state)
            if (!state.sidebarCollapsed) {
                LibraryBlock(state)
                YearBlock(state)
            }
            Spacer(Modifier.height(8.dp))
            NavList(state)
            Spacer(Modifier.weight(1f))
            SettingsButton(state)
        }
    }
}

@Composable
private fun SidebarHead(state: AppState) {
    Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.sidebarCollapsed = !state.sidebarCollapsed }) {
            Text("☰")
        }
        if (!state.sidebarCollapsed) {
            Text("Osprey", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryBlock(state: AppState) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(state.libraryPath?.toString().orEmpty(), fontSize = 11.sp)
    }
}

/** The cog: opens the Settings screen, or returns to the actions when it is already open. */
@Composable
private fun SettingsButton(state: AppState) {
    Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.settingsState.showing = !state.settingsState.showing }) {
            Text("⚙")
        }
        if (!state.sidebarCollapsed) {
            Text("Settings")
        }
    }
}

@Composable
private fun YearBlock(state: AppState) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text("Year", fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.selectedYear ?: "--")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year) },
                        onClick = {
                            state.selectedYear = year
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavList(state: AppState) {
    state.visibleTabs.forEach { tab ->
        val selected = state.selectedTab == tab
        val colors = if (selected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        }
        Button(
            onClick = { state.selectedTab = tab },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            colors = colors,
        ) {
            Text(if (state.sidebarCollapsed) tab.label.take(1) else tab.label)
        }
    }
}
