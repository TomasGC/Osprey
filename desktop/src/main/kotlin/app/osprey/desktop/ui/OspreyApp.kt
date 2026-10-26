package app.osprey.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.osprey.desktop.ActionTab
import app.osprey.desktop.AppState
import app.osprey.desktop.ActionUiState
import app.osprey.desktop.SimpleActionController
import app.osprey.filesystem.FileHandle

/** App shell: collapsible sidebar + a content area switching between the five simple-action tabs. */
@Composable
fun OspreyApp(state: AppState) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(state)
                    Box(modifier = Modifier.fillMaxHeight()) {
                        OspreyContent(state)
                    }
                }
                JobStrip(state.jobs)
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
    if (state.showsTrash && libraryDir != null) {
        TrashScreen(state, libraryDir)
        return
    }
    val rootAction = libraryDir?.let { rootActionFor(state, state.selectedTab, it) }
    if (rootAction != null) {
        ActionPane(rootAction.ui, rootAction.scan, rootAction.apply)
        return
    }
    YearContent(state)
}

/** The selected action on the selected year folder. */
@Composable
private fun YearContent(state: AppState) {
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

/** A pane that runs on the library root: what it shows, how it scans and applies, and whether it can stop. */
private class RootAction(
    val ui: ActionUiState,
    val scan: () -> Unit,
    val apply: () -> Unit,
)

/** The tabs that run on the library root instead of the selected year folder. */
private fun rootActionFor(state: AppState, tab: ActionTab, root: FileHandle): RootAction? = when (tab) {
    ActionTab.REORG -> RootAction(
        state.reorg.state,
        { state.reorg.onScan(root) },
        { state.reorg.onApply(root) },
    )
    ActionTab.TRASH_MIGRATION -> RootAction(
        state.trashMigration.state,
        { state.trashMigration.onScan(root) },
        { state.trashMigration.onApply(root) },
    )
    else -> null
}

private fun controllerFor(state: AppState, tab: ActionTab): SimpleActionController<*> = when (tab) {
    ActionTab.FLATTEN -> state.flatten
    ActionTab.REORG -> error("Reorg is handled separately above")
    ActionTab.TRASH_MIGRATION -> error("Trash migration is handled separately above")
    ActionTab.RENAME -> state.rename
    ActionTab.DEDUPE -> state.dedupe
    ActionTab.DATES -> state.dates
    ActionTab.STANDARDIZE -> state.standardize
    ActionTab.VISUAL -> error("Visual Dupes is handled separately above")
}
