package app.osprey.android.media

import android.content.Context
import app.osprey.media.MediaMetadataReader
import app.osprey.media.UprightMediaMetadataReader

/** The reader the app uses: platform decoding, every frame turned upright (#51). */
fun uprightAndroidMediaMetadataReader(context: Context): MediaMetadataReader =
    UprightMediaMetadataReader(AndroidMediaMetadataReader(context), AndroidOrientationSource(context))
