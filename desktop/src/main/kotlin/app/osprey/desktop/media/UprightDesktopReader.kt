package app.osprey.desktop.media

import app.osprey.media.MediaMetadataReader
import app.osprey.media.UprightMediaMetadataReader

/** The reader the app uses: platform decoding, every frame turned upright (#51). */
fun uprightDesktopMediaMetadataReader(): MediaMetadataReader =
    UprightMediaMetadataReader(DesktopMediaMetadataReader(), DesktopOrientationSource())
