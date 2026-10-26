package io.github.markyu0401.everheard.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.markyu0401.everheard.MainActivity
import io.github.markyu0401.everheard.R

/**
 * The visible-capture notification. It is ongoing and silent; the start tone is
 * played by the service. Actions are pause, mark, and stop.
 */
object CaptureNotification {

    const val CHANNEL_ID = "capture"
    const val RESUME_CHANNEL_ID = "capture_resume"
    const val NOTIF_ID = 1001
    const val RESUME_NOTIF_ID = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val capture = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val resume = NotificationChannel(
            RESUME_CHANNEL_ID,
            context.getString(R.string.notification_resume_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_resume_channel_description)
            setShowBadge(true)
        }

        manager.createNotificationChannel(capture)
        manager.createNotificationChannel(resume)
    }

    fun build(context: Context, stateText: String, detail: String? = null): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pause15 = serviceAction(context, 1, CaptureService.ACTION_PAUSE_15)
        val pause60 = serviceAction(context, 2, CaptureService.ACTION_PAUSE_60)
        val pauseTomorrow = serviceAction(context, 6, CaptureService.ACTION_PAUSE_TOMORROW)
        val mark = serviceAction(context, 3, CaptureService.ACTION_MARK)
        val stop = serviceAction(context, 4, CaptureService.ACTION_STOP)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_everheard)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(stateText)
            .setSubText(detail)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notification_action_pause_15), pause15)
            .addAction(0, context.getString(R.string.notification_action_pause_60), pause60)
            .addAction(0, context.getString(R.string.notification_action_pause_tomorrow), pauseTomorrow)
            .addAction(0, context.getString(R.string.notification_action_mark), mark)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .build()
    }

    fun buildResumeNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            5,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_everheard)
            .setContentTitle(context.getString(R.string.notification_resume_title))
            .setContentText(context.getString(R.string.notification_resume_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun serviceAction(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, CaptureService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
