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
import app.osprey.desktop.pickFolder

/** App shell: collapsible sidebar + a content area switching between the five simple-action tabs. */
@Composable
fun OspreyApp(state: AppState) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(state, onChangeLibrary = { pickFolder()?.let { state.openLibrary(it) } })
                Box(modifier = Modifier.fillMaxHeight()) {
                    OspreyContent(state)
                }
            }
        }
    }
}

@Composable
private fun OspreyContent(state: AppState) {
    if (state.libraryPath == null) {
        NoLibraryScreen(onChooseLibrary = { pickFolder()?.let { state.openLibrary(it) } })
        return
    }
    val yearDir = state.yearHandle()
    if (yearDir == null) {
        Text("Pick a year to get started.")
        return
    }
    val controller = controllerFor(state, state.selectedTab)
    ActionPane(controller.state, { controller.onScan(yearDir) }, { controller.onApply(yearDir) })
}

private fun controllerFor(state: AppState, tab: ActionTab): SimpleActionController<*> = when (tab) {
    ActionTab.FLATTEN -> state.flatten
    ActionTab.RENAME -> state.rename
    ActionTab.DEDUPE -> state.dedupe
    ActionTab.DATES -> state.dates
    ActionTab.STANDARDIZE -> state.standardize
}
