import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.osprey.desktop.AppState
import app.osprey.desktop.DesktopSettingsStore
import app.osprey.desktop.OspreyConfig
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.ui.OspreyApp
import app.osprey.desktop.ui.TaskbarEffect

private fun parseLibraryArg(args: Array<String>): String? {
    val index = args.indexOf("--library")
    return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
}

fun main(args: Array<String>) = application {
    // Once: a state made again would also make a new job queue, and orphan the jobs running in the old one.
    val state = remember {
        AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader(), DesktopSettingsStore()).also {
            OspreyConfig.resolveLibraryPath(parseLibraryArg(args))
            it.start()
        }
    }

    Window(onCloseRequest = ::exitApplication, title = "Osprey") {
        OspreyApp(state)
        TaskbarEffect(state.jobs, window)
    }
}
