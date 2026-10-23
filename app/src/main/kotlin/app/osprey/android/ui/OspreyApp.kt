package app.osprey.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.android.ActionTab
import app.osprey.android.AndroidAppState
import app.osprey.android.ActionUiState
import app.osprey.android.SimpleActionController
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.launch

private const val DATES_LIMITATION =
    "Android can't write file timestamps (storage scoping, confirmed in #13). " +
        "Scan still shows what would be fixed -- Apply can't change anything here."

/** App shell: a drawer (library/year picker + the six action screens) behind a top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OspreyApp(state: AndroidAppState) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        state = state,
                        onOpenSettings = {
                            state.showsTrash = false
                            state.settingsState.showing = true
                            scope.launch { drawerState.close() }
                        },
                        onOpenTrash = {
                            state.toggleTrash()
                            scope.launch { drawerState.close() }
                        },
                        onTabSelected = { tab ->
                            state.selectedTab = tab
                            state.showsTrash = false
                            state.settingsState.showing = false
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(screenTitle(state)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰", fontSize = 20.sp)
                            }
                        },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    OspreyContent(state)
                }
            }
        }
    }
}

@Composable
private fun OspreyContent(state: AndroidAppState) {
    if (state.showsSettings) {
        SettingsScreen(state)
        return
    }
    val libraryRoot = state.libraryRoot ?: return
    if (state.showsTrash) {
        TrashScreen(state, libraryRoot)
        return
    }
    val rootAction = rootActionFor(state, state.selectedTab, libraryRoot)
    if (rootAction != null) {
        ActionPane(rootAction.ui, rootAction.scan, rootAction.apply, onCancel = rootAction.cancel)
        return
    }
    YearContent(state)
}

/** The selected action on the selected year folder. */
@Composable
private fun YearContent(state: AndroidAppState) {
    val yearDir = state.yearHandle()
    if (yearDir == null) {
        Text("Pick a year from the menu (☰) to get started.", modifier = Modifier.padding(24.dp))
        return
    }
    when (state.selectedTab) {
        ActionTab.VISUAL -> VisualDupesScreen(state, yearDir)
        ActionTab.DATES -> ActionPane(
            state.dates.state,
            { state.dates.onScan(yearDir) },
            { state.dates.onApply(yearDir) },
            applyBlockedReason = DATES_LIMITATION,
        )
        else -> {
            val controller = controllerFor(state, state.selectedTab)
            ActionPane(controller.state, { controller.onScan(yearDir) }, { controller.onApply(yearDir) })
        }
    }
}

/** A pane that runs on the library root: what it shows, how it scans and applies, and whether it can stop. */
private class RootAction(
    val ui: ActionUiState,
    val scan: suspend () -> Unit,
    val apply: suspend () -> Unit,
    val cancel: (() -> Unit)? = null,
)

/** The tabs that run on the library root instead of the selected year folder. */
private fun rootActionFor(state: AndroidAppState, tab: ActionTab, root: FileHandle): RootAction? = when (tab) {
    ActionTab.REORG -> RootAction(
        state.reorg.state,
        { state.reorg.onScan(root) },
        {
            state.reorg.onApply(root)
            state.refreshYears()
        },
        state.reorg::onCancel,
    )
    ActionTab.TRASH_MIGRATION -> RootAction(
        state.trashMigration.state,
        { state.trashMigration.onScan(root) },
        {
            state.trashMigration.onApply(root)
            state.refreshYears()
            state.refreshLegacyTrash()
        },
    )
    else -> null
}

private fun controllerFor(state: AndroidAppState, tab: ActionTab): SimpleActionController<*> = when (tab) {
    ActionTab.FLATTEN -> state.flatten
    ActionTab.REORG -> error("Reorg is handled separately above")
    ActionTab.TRASH_MIGRATION -> error("Trash migration is handled separately above")
    ActionTab.RENAME -> state.rename
    ActionTab.DEDUPE -> state.dedupe
    ActionTab.STANDARDIZE -> state.standardize
    ActionTab.DATES -> error("Dates is handled separately above")
    ActionTab.VISUAL -> error("Visual Dupes is handled separately above")
}

@Composable
private fun DrawerContent(
    state: AndroidAppState,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onTabSelected: (ActionTab) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Osprey", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text(libraryLabel(state.libraryRoot), fontSize = 12.sp)
        if (state.years.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Year", fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            YearPicker(state)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        state.visibleTabs.forEach { tab ->
            NavigationDrawerItem(
                label = { Text(tab.label) },
                selected = state.selectedTab == tab && !state.showsSettings && !state.showsTrash,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        HorizontalDivider()
        Row {
            NavigationDrawerItem(
                label = { Text("⚙ Settings") },
                selected = state.showsSettings,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            )
            NavigationDrawerItem(
                label = { Text("🗑 Trash") },
                selected = state.showsTrash && !state.showsSettings,
                onClick = onOpenTrash,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            )
        }
    }
}

private fun screenTitle(state: AndroidAppState): String = when {
    state.showsSettings -> "Settings"
    state.showsTrash -> "Trash"
    else -> state.selectedTab.label
}

@Composable
private fun YearPicker(state: AndroidAppState) {
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

private fun libraryLabel(root: FileHandle?): String =
    (root as? AndroidFileHandle)?.documentFile?.name?.let { "Library: $it" } ?: "No library selected"
