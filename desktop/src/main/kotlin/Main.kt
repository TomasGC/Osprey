import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.osprey.desktop.AppState
import app.osprey.desktop.OspreyConfig
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.ui.OspreyApp

private fun parseLibraryArg(args: Array<String>): String? {
    val index = args.indexOf("--library")
    return if (index >= 0 && index + 1 < args.size) args[index + 1] else null
}

fun main(args: Array<String>) = application {
    val state = AppState(DesktopFileSystemRepository(), DesktopMediaMetadataReader())
    OspreyConfig.resolveLibraryPath(parseLibraryArg(args))?.let { state.openLibrary(it) }

    Window(onCloseRequest = ::exitApplication, title = "Osprey") {
        OspreyApp(state)
    }
}
