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
 *
 * A second, high-importance channel carries one transient alert when another
 * app takes the microphone while a recording is running. The ongoing service
 * notification cannot alert (it is LOW importance and onlyAlertOnce), so
 * without this channel the pause is invisible unless the user opens the shade.
 */
object CaptureNotification {

    const val CHANNEL_ID = "capture"
    const val RESUME_CHANNEL_ID = "capture_resume"
    const val ALERT_CHANNEL_ID = "capture_alert"
    const val ALERT_SILENT_CHANNEL_ID = "capture_alert_silent"
    const val NOTIF_ID = 1001
    const val RESUME_NOTIF_ID = 1002
    const val ALERT_NOTIF_ID = 1003

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

        val alert = NotificationChannel(
            ALERT_CHANNEL_ID,
            context.getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_alert_channel_description)
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 100, 150)
            setSound(null, null)
        }

        // Same heads-up importance, no vibration. Used when the user turns the
        // "vibrate when the mic is taken" setting off; the visual alert stays.
        val alertSilent = NotificationChannel(
            ALERT_SILENT_CHANNEL_ID,
            context.getString(R.string.notification_alert_silent_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_alert_channel_description)
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(capture)
        manager.createNotificationChannel(resume)
        manager.createNotificationChannel(alert)
        manager.createNotificationChannel(alertSilent)
    }

    fun build(context: Context, stateText: String, detail: String? = null): Notification {
        val openApp = openAppIntent(context, requestCode = 0)
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
        return NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_everheard)
            .setContentTitle(context.getString(R.string.notification_resume_title))
            .setContentText(context.getString(R.string.notification_resume_body))
            .setContentIntent(openAppIntent(context, requestCode = 5))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /**
     * Posted when the service stops on a real capture failure. The foreground
     * notification is removed with the service, so without this the user would
     * only see "Not recording" after opening the app.
     */
    fun buildErrorNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_everheard)
            .setContentTitle(context.getString(R.string.notification_error_title))
            .setContentText(context.getString(R.string.notification_error_body))
            .setContentIntent(openAppIntent(context, requestCode = 8))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /**
     * Transient heads-up: the recording was actually interrupted, not merely
     * waiting for a busy mic. Auto-cancels after 10 s; the ongoing notification
     * keeps the current state afterwards.
     */
    fun buildMicAlert(context: Context, vibrate: Boolean = true): Notification {
        val body = context.getString(R.string.notification_mic_alert_body)
        val builder = NotificationCompat.Builder(
            context,
            if (vibrate) ALERT_CHANNEL_ID else ALERT_SILENT_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_stat_everheard)
            .setContentTitle(context.getString(R.string.notification_mic_alert_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context, requestCode = 7))
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (vibrate) {
            builder.setVibrate(longArrayOf(0, 150, 100, 150))
        }
        return builder.build()
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun serviceAction(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, CaptureService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
