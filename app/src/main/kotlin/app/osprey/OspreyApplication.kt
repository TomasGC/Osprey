package app.osprey

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.osprey.android.AndroidAppState
import app.osprey.android.AndroidSettingsStore
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.AndroidFileSystemRepository
import app.osprey.android.jobs.JobService
import app.osprey.android.media.uprightAndroidMediaMetadataReader
import app.osprey.android.releaseGrant
import app.osprey.history.JobHistoryStore
import app.osprey.history.RunRecorder
import app.osprey.history.openHistory
import app.osprey.jobs.JobEntry
import app.osprey.jobs.JobQueue
import app.osprey.jobs.startedNewJob
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns what has to outlive a screen: the job queue and the app state. A run keeps going when the activity is
 * rotated, closed or sent to the background, and the next activity finds the same state, its plan and its progress.
 * It also starts [JobService] when a job is waiting or running, which is what keeps the process alive.
 */
class OspreyApplication : Application() {

    private val watcher = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var startedActivities = 0

    /** Jobs are blocking file work, so they get the threads made for that, not the ones that compute. */
    val history: JobHistoryStore by lazy { openHistory(File(filesDir, "history")) }

    val jobs: JobQueue by lazy {
        JobQueue(CoroutineScope(SupervisorJob() + Dispatchers.IO), RunRecorder(history))
    }

    /** True while one of the app's screens is on show, so a finished job needs no notification. */
    val isVisible: Boolean get() = startedActivities > 0

    val appState: AndroidAppState by lazy {
        AndroidAppState(
            fs = AndroidFileSystemRepository(this),
            mediaMetadataReader = uprightAndroidMediaMetadataReader(this),
            settingsStore = AndroidSettingsStore(this),
            jobs = jobs,
            resolveFolder = { folder ->
                DocumentFile.fromTreeUri(this, Uri.parse(folder.reference))
                    ?.takeIf { it.isDirectory }
                    ?.let(::AndroidFileHandle)
            },
            releaseGrant = { releaseGrant(this, it) },
            history = history,
        )
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(visibility)
        watcher.launch {
            var known = emptyList<JobEntry>()
            jobs.entries.collect { entries ->
                if (startedNewJob(known, entries)) startService()
                known = entries
            }
        }
    }

    /** Every new job asks for the service: a running one answers the command, a stopping one is kept (#111). */
    private fun startService() = ContextCompat.startForegroundService(this, Intent(this, JobService::class.java))

    private val visibility = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
