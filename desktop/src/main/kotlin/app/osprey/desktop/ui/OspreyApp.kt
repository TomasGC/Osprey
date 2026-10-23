package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.osprey.desktop.ActionTab
import app.osprey.desktop.AppState
import app.osprey.desktop.SimpleActionController

/** App shell: collapsible sidebar + a content area switching between the five simple-action tabs. */
@Composable
fun OspreyApp(state: AppState) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(state)
                Box(modifier = Modifier.fillMaxHeight()) {
                    OspreyContent(state)
                }
            }
        }
    }
}

@Composable
private fun OspreyContent(state: AppState) {
    if (state.settingsState.showing || state.libraryPath == null) {
        SettingsScreen(state)
        return
    }
    val libraryDir = state.libraryHandle()
    val rootController = rootControllerFor(state, state.selectedTab)
    if (rootController != null && libraryDir != null) {
        ActionPane(
            rootController.state,
            { rootController.onScan(libraryDir) },
            {
                rootController.onApply(libraryDir)
                state.refreshYears()
                state.refreshLegacyTrash()
            },
        )
        return
    }
    val yearDir = state.yearHandle()
    if (yearDir == null) {
        Text("Pick a year to get started.")
        return
    }
    if (state.selectedTab == ActionTab.VISUAL) {
        VisualDupesScreen(state, yearDir)
        return
    }
    val controller = controllerFor(state, state.selectedTab)
    ActionPane(controller.state, { controller.onScan(yearDir) }, { controller.onApply(yearDir) })
}

/** The tabs that run on the library root instead of the selected year folder. */
private fun rootControllerFor(state: AppState, tab: ActionTab): SimpleActionController<*>? = when (tab) {
    ActionTab.YEAR_FOLDERS -> state.yearFolders
    ActionTab.TRASH_MIGRATION -> state.trashMigration
    else -> null
}

private fun controllerFor(state: AppState, tab: ActionTab): SimpleActionController<*> = when (tab) {
    ActionTab.FLATTEN -> state.flatten
    ActionTab.YEAR_FOLDERS -> error("Year folders is handled separately above")
    ActionTab.TRASH_MIGRATION -> error("Trash migration is handled separately above")
    ActionTab.RENAME -> state.rename
    ActionTab.DEDUPE -> state.dedupe
    ActionTab.DATES -> state.dates
    ActionTab.STANDARDIZE -> state.standardize
    ActionTab.VISUAL -> error("Visual Dupes is handled separately above")
}
