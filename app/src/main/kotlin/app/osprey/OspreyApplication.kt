package app.osprey

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.osprey.android.AndroidAppState
import app.osprey.android.AndroidSettingsStore
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.AndroidFileSystemRepository
import app.osprey.android.media.AndroidMediaMetadataReader
import app.osprey.android.releaseGrant
import app.osprey.jobs.JobQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns what has to outlive a screen: the job queue and the app state. A run keeps going when the activity is
 * rotated, closed or sent to the background, and the next activity finds the same state, its plan and its progress.
 */
class OspreyApplication : Application() {

    val jobs = JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    val appState: AndroidAppState by lazy {
        AndroidAppState(
            fs = AndroidFileSystemRepository(this),
            mediaMetadataReader = AndroidMediaMetadataReader(this),
            settingsStore = AndroidSettingsStore(this),
            jobs = jobs,
            resolveFolder = { folder ->
                DocumentFile.fromTreeUri(this, Uri.parse(folder.reference))
                    ?.takeIf { it.isDirectory }
                    ?.let(::AndroidFileHandle)
            },
            releaseGrant = { releaseGrant(this, it) },
        )
    }
}
