package app.osprey

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import app.osprey.android.AndroidAppState
import app.osprey.android.AndroidSettingsStore
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.AndroidFileSystemRepository
import app.osprey.android.media.AndroidMediaMetadataReader
import app.osprey.android.releaseGrant
import app.osprey.android.ui.OspreyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val state = remember {
                AndroidAppState(
                    fs = AndroidFileSystemRepository(context),
                    mediaMetadataReader = AndroidMediaMetadataReader(context),
                    settingsStore = AndroidSettingsStore(context),
                    resolveFolder = { folder ->
                        DocumentFile.fromTreeUri(context, Uri.parse(folder.reference))
                            ?.takeIf { it.isDirectory }
                            ?.let(::AndroidFileHandle)
                    },
                    releaseGrant = { releaseGrant(context, it) },
                )
            }
            LaunchedEffect(Unit) { state.start() }
            OspreyApp(state)
        }
    }
}
