package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.history.openHistory

/** The app's state with its real parts: plain-path file system, upright decoding, the settings file, the history. */
fun desktopAppState(): AppState =
    AppState(
        DesktopFileSystemRepository(), uprightDesktopMediaMetadataReader(), DesktopSettingsStore(),
        history = openHistory(OspreyConfig.historyDir()),
    )
