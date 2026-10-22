package app.osprey.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
fun OspreyApp(state: AndroidAppState, onChooseLibrary: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        state = state,
                        onChangeLibrary = onChooseLibrary,
                        onTabSelected = { tab ->
                            state.selectedTab = tab
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(state.selectedTab.label) },
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
    if (state.libraryRoot == null) {
        Text("Pick a library from the menu (☰) to get started.", modifier = Modifier.padding(24.dp))
        return
    }
    val libraryRoot = state.libraryRoot ?: return
    val rootController = rootControllerFor(state, state.selectedTab)
    if (rootController != null) {
        ActionPane(
            rootController.state,
            { rootController.onScan(libraryRoot) },
            {
                rootController.onApply(libraryRoot)
                state.refreshYears()
                state.refreshLegacyTrash()
            },
        )
        return
    }
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

/** The tabs that run on the library root instead of the selected year folder. */
private fun rootControllerFor(state: AndroidAppState, tab: ActionTab): SimpleActionController<*>? = when (tab) {
    ActionTab.YEAR_FOLDERS -> state.yearFolders
    ActionTab.TRASH_MIGRATION -> state.trashMigration
    else -> null
}

private fun controllerFor(state: AndroidAppState, tab: ActionTab): SimpleActionController<*> = when (tab) {
    ActionTab.FLATTEN -> state.flatten
    ActionTab.YEAR_FOLDERS -> error("Year folders is handled separately above")
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
    onChangeLibrary: () -> Unit,
    onTabSelected: (ActionTab) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Osprey", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text(libraryLabel(state.libraryRoot), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChangeLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("Change library")
        }
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
                selected = state.selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
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
