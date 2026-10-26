package io.github.markyu0401.everheard.capture

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.markyu0401.everheard.R

/**
 * The capture skeleton. It owns the foreground notification and the start tone;
 * AudioRecord arrives in M0-T4. It never starts itself from the background:
 * Android refuses a microphone foreground service created there.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "io.github.markyu0401.everheard.action.START"
        const val ACTION_STOP = "io.github.markyu0401.everheard.action.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val TAG = "CaptureService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        CaptureNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCaptureForeground()
        return START_STICKY
    }

    private fun startCaptureForeground() {
        val notification = CaptureNotification.build(
            this,
            getString(R.string.notification_state_armed)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CaptureNotification.NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(CaptureNotification.NOTIF_ID, notification)
            }
            isRunning = true
            playStartTone()
        } catch (error: Exception) {
            // Android refuses a microphone foreground service created from the
            // background. Do not crash: post a tap-to-resume notification.
            Log.e(TAG, "Unable to start capture foreground service", error)
            isRunning = false
            postResumeNotification()
            stopSelf()
        }
    }

    private fun postResumeNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        NotificationManagerCompat.from(this).notify(
            CaptureNotification.RESUME_NOTIF_ID,
            CaptureNotification.buildResumeNotification(this)
        )
    }

    private fun playStartTone() {
        // The tone is the audible half of the visible-capture promise. Keep it
        // short; release the generator after it finishes.
        val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        toneGenerator = generator
        generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
        mainHandler.postDelayed({
            generator.release()
            if (toneGenerator === generator) toneGenerator = null
        }, 400)
    }

    override fun onDestroy() {
        isRunning = false
        toneGenerator?.release()
        toneGenerator = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
