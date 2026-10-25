package app.osprey.android.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.osprey.MainActivity
import app.osprey.R
import app.osprey.jobs.JobNotification

internal const val ACTION_STOP = "app.osprey.action.STOP_JOB"
internal const val EXTRA_JOB_ID = "app.osprey.extra.JOB_ID"
internal const val ONGOING_ID = 1
private const val RESULT_ID_BASE = 1000
private const val CHANNEL_ONGOING = "osprey_jobs"
private const val CHANNEL_RESULT = "osprey_job_results"
private const val FULL_PERCENT = 100

/**
 * Turns the words and numbers `:core` decided ([JobNotification]) into Android notifications: one ongoing
 * notification with a progress bar and a Stop button while jobs run, and one result per finished job.
 */
internal class JobNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Running jobs", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Finished jobs", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun ongoing(model: JobNotification) = NotificationCompat.Builder(context, CHANNEL_ONGOING)
        .setSmallIcon(R.drawable.ic_stat_job)
        .setContentTitle(model.title)
        .setContentText(model.text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp())
        .apply {
            if (model.percent != null || model.indeterminate) {
                setProgress(FULL_PERCENT, model.percent ?: 0, model.indeterminate)
            }
            model.stoppableJobId?.let { addAction(0, "Stop", stopIntent(it)) }
        }
        .build()

    fun showResult(jobId: Long, model: JobNotification) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_job)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(model.text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setCategory(if (model.failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(RESULT_ID_BASE + jobId.toInt(), notification)
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
