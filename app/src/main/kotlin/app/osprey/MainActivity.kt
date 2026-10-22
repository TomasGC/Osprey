package app.osprey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import app.osprey.android.AndroidAppState
import app.osprey.android.AndroidOspreyConfig
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.AndroidFileSystemRepository
import app.osprey.android.media.AndroidMediaMetadataReader
import app.osprey.android.rememberFolderPicker
import app.osprey.android.ui.NoLibraryScreen
import app.osprey.android.ui.OspreyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val state = remember {
                AndroidAppState(AndroidFileSystemRepository(context), AndroidMediaMetadataReader(context))
            }
            val pickFolder = rememberFolderPicker { uri ->
                DocumentFile.fromTreeUri(context, uri)?.let { state.openLibrary(AndroidFileHandle(it)) }
            }
            LaunchedEffect(Unit) {
                AndroidOspreyConfig.resolveLibraryRoot(context)?.let { state.openLibrary(AndroidFileHandle(it)) }
            }
            if (state.libraryRoot == null) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    NoLibraryScreen(onChooseLibrary = pickFolder)
                }
            } else {
                OspreyApp(state, onChooseLibrary = pickFolder)
            }
        }
    }
}
