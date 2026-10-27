import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.osprey.desktop.OspreyConfig
import app.osprey.desktop.desktopAppState
import app.osprey.desktop.ui.CloseGuard
import app.osprey.desktop.ui.OspreyApp
import app.osprey.desktop.ui.TaskbarEffect
import app.osprey.jobs.closeRisk

private fun parseLibraryArg(args: Array<String>): String? {
    val index = args.indexOf("--library")
    return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
}

fun main(args: Array<String>) {
    // Built and started here, outside composition: `start()` reads Compose state, and a read made while composing
    // subscribes the composing scope to it, so a later write would recompose `application` itself.
    val state = desktopAppState().also {
        OspreyConfig.resolveLibraryPath(parseLibraryArg(args))
        it.start()
    }
    application {
        var asking by remember { mutableStateOf(false) }

        Window(
            // The entries are read here, in the click, not in this scope: reading them would recompose it on every job.
            onCloseRequest = { if (closeRisk(state.jobs.entries.value)) asking = true else exitApplication() },
            title = "Osprey",
        ) {
            OspreyApp(state)
            TaskbarEffect(state.jobs, window)
            if (asking) CloseGuard(state.jobs, onKeep = { asking = false }, onQuit = ::exitApplication)
        }
    }
}
