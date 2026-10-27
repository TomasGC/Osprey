package app.osprey.desktop

import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader

/** The app's state with its real parts: plain-path file system, upright decoding, the settings file. */
fun desktopAppState(): AppState =
    AppState(DesktopFileSystemRepository(), uprightDesktopMediaMetadataReader(), DesktopSettingsStore())
