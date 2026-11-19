package app.osprey.android.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import app.osprey.MainActivity
import app.osprey.R
import app.osprey.jobs.JobNotification

internal const val ACTION_STOP = "app.osprey.action.STOP_JOB"
internal const val EXTRA_JOB_ID = "app.osprey.extra.JOB_ID"
internal const val ONGOING_ID = 1
private const val RESULT_ID_BASE = 1000
private const val CHANNEL_ONGOING = "osprey_jobs_v2"
private const val CHANNEL_ONGOING_LEGACY = "osprey_jobs"
private const val CHANNEL_RESULT = "osprey_job_results"
private const val SORT_RUNNING = "0"
private const val SORT_RESULT = "1"
private const val FULL_PERCENT = 100

/**
 * Turns the words and numbers `:core` decided ([JobNotification]) into Android notifications: one ongoing
 * notification with a progress bar and a Stop button while jobs run, and one result per finished job.
 */
class JobNotifications(
    private val context: Context,
    private val runningChannelId: String = CHANNEL_ONGOING,
    private val legacyRunningChannelId: String = CHANNEL_ONGOING_LEGACY,
) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * The running channel has the importance of the results', silent, so the shade ranks the two by their sort keys
     * and the running card stays in front (#114). A channel's importance cannot be changed once it exists, so the
     * running channel has a new id and the old one is deleted. A user who had switched the old one off keeps it off:
     * the new channel then starts blocked, and one that already exists keeps whatever the user set on it.
     */
    fun createChannels() {
        val legacy = manager.getNotificationChannel(legacyRunningChannelId)
        val blocked = legacy?.importance == NotificationManager.IMPORTANCE_NONE
        manager.deleteNotificationChannel(legacyRunningChannelId)
        val importance = if (blocked) NotificationManager.IMPORTANCE_NONE else NotificationManager.IMPORTANCE_DEFAULT
        manager.createNotificationChannel(
            NotificationChannel(runningChannelId, "Running jobs", importance).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Finished jobs", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun ongoing(model: JobNotification) = NotificationCompat.Builder(context, runningChannelId)
        .setSmallIcon(R.drawable.ic_stat_job)
        .setContentTitle(model.title)
        .setContentText(model.text)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(ongoingView(model))
        .setOngoing(true)
        .setSortKey(SORT_RUNNING)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp())
        .apply {
            if (model.percent != null || model.indeterminate) {
                setProgress(FULL_PERCENT, model.percent ?: 0, model.indeterminate)
            }
        }
        .build()

    /** The running notification's own layout, so Stop is on screen even when the system shows it collapsed (#94). */
    fun ongoingView(model: JobNotification): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_job_running).apply {
            setTextViewText(R.id.notification_title, model.title)
            setTextViewText(R.id.notification_text, model.text)
            val hasBar = model.percent != null || model.indeterminate
            setViewVisibility(R.id.notification_progress, if (hasBar) View.VISIBLE else View.GONE)
            setProgressBar(R.id.notification_progress, FULL_PERCENT, model.percent ?: 0, model.indeterminate)
            val jobId = model.stoppableJobId
            setViewVisibility(R.id.notification_stop, if (jobId == null) View.GONE else View.VISIBLE)
            if (jobId != null) {
                setTextViewText(R.id.notification_stop, "Stop")
                setOnClickPendingIntent(R.id.notification_stop, stopIntent(jobId))
            }
        }

    fun showResult(jobId: Long, model: JobNotification) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_job)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(model.text))
            .setAutoCancel(true)
            .setSortKey(SORT_RESULT)
            .setContentIntent(openApp())
            .setCategory(if (model.failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(RESULT_ID_BASE + jobId.toInt(), notification)
    }

    /** Removes the result notifications of earlier jobs (#94), so the running one has nothing to stack with. */
    fun clearResults() {
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_RESULT }
            .forEach { manager.cancel(it.id) }
    }

    fun showOngoing(model: JobNotification) = manager.notify(ONGOING_ID, ongoing(model))

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(jobId: Long): PendingIntent = PendingIntent.getService(
        context,
        jobId.toInt(),
        Intent(context, JobService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_JOB_ID, jobId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
