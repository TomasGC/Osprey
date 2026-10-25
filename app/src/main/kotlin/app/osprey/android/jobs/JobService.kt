package app.osprey.android.jobs

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.osprey.OspreyApplication
import app.osprey.jobs.JobNotification
import app.osprey.jobs.newlyFinished
import app.osprey.jobs.ongoingNotification
import app.osprey.jobs.resultNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val NO_JOB = -1L

/**
 * Keeps the process alive while jobs run, so a run goes on with the app in the background or the screen off.
 * It shows the ongoing notification (progress, Stop) and a result per finished job, and ends itself when the
 * queue is empty. The jobs themselves belong to [OspreyApplication]; this service only watches them.
 */
class JobService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = application as OspreyApplication
    private lateinit var notifications: JobNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = JobNotifications(this)
        notifications.createChannels()
        // Android wants the service in the foreground within seconds of starting it, whatever the queue holds.
        val first = ongoingNotification(app.jobs.entries.value)
            ?: JobNotification("Osprey", "Working...", indeterminate = true)
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ServiceCompat.startForeground(this, ONGOING_ID, notifications.ongoing(first), type)
        scope.launch { watch() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) app.jobs.stop(intent.getLongExtra(EXTRA_JOB_ID, NO_JOB))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        app.jobServiceStopped()
        super.onDestroy()
    }

    private suspend fun watch() {
        var seen = app.jobs.entries.value
        app.jobs.entries.collect { entries ->
            if (!app.isVisible) {
                newlyFinished(seen, entries).forEach { entry ->
                    resultNotification(entry)?.let { notifications.showResult(entry.id, it) }
                }
            }
            seen = entries
            val ongoing = ongoingNotification(entries)
            if (ongoing == null) {
                app.jobServiceStopped()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                notifications.showOngoing(ongoing)
            }
        }
    }
}
