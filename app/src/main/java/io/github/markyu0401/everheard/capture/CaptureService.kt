package io.github.markyu0401.everheard.capture

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.audio.RingBuffer
import io.github.markyu0401.everheard.capture.audio.WavWriter
import io.github.markyu0401.everheard.capture.guards.BatteryGuard
import io.github.markyu0401.everheard.capture.guards.SilenceTracker
import io.github.markyu0401.everheard.capture.guards.ThermalGuard
import io.github.markyu0401.everheard.capture.schedule.ScheduleEvaluator
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.capture.vad.SileroVad
import io.github.markyu0401.everheard.data.settings.scheduleStore
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One long-lived armed service. Schedule, battery, thermal, and silence rules
 * gate AudioRecord; the service itself is never stopped by a schedule change,
 * because Android refuses to create a microphone foreground service from the
 * background.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "io.github.markyu0401.everheard.action.START"
        const val ACTION_STOP = "io.github.markyu0401.everheard.action.STOP"
        const val ACTION_PAUSE_15 = "io.github.markyu0401.everheard.action.PAUSE_15"
        const val ACTION_PAUSE_60 = "io.github.markyu0401.everheard.action.PAUSE_60"
        const val ACTION_MARK = "io.github.markyu0401.everheard.action.MARK"

        private const val TAG = "CaptureService"
        private const val TICK_MS = 30_000L
        private const val MARK_SECONDS = 30

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var scheduleJob: Job? = null
    private var scheduleRules: List<ScheduleRule> = ScheduleEvaluator.defaultRules()

    private var state: CaptureState = CaptureState.STOPPED
    private var pauseUntilMillis: Long = 0L
    private var batteryLevel: Int = 100

    private var audioSource: AudioRecordSource? = null
    private var vad: SileroVad? = null
    private val ringBuffer = RingBuffer(capacitySamples = 60 * 16_000)
    private val silenceTracker = SilenceTracker(clock = { SystemClock.elapsedRealtime() })

    private var toneGenerator: ToneGenerator? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel) ?: batteryLevel
            evaluate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CaptureNotification.createChannels(this)
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scheduleJob = serviceScope.launch {
            scheduleStore().rules.collect { rules ->
                scheduleRules = rules
                evaluate()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_15 -> {
                pauseFor(minutes = 15)
                return START_STICKY
            }
            ACTION_PAUSE_60 -> {
                pauseFor(minutes = 60)
                return START_STICKY
            }
            ACTION_MARK -> {
                markLastThirtySeconds()
                return START_STICKY
            }
            else -> startCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
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
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start capture foreground service", error)
            isRunning = false
            postResumeNotification()
            stopSelf()
            return
        }

        state = CaptureState.ARMED
        isRunning = true
        playStartTone()
        evaluate()

        if (tickJob == null) {
            tickJob = serviceScope.launch {
                while (isActive) {
                    evaluate()
                    delay(TICK_MS)
                }
            }
        }
    }

    private fun evaluate() {
        val now = System.currentTimeMillis()
        val scheduleAllows = ScheduleEvaluator.isAllowed(
            scheduleRules,
            LocalDateTime.now()
        )
        val batteryOk = BatteryGuard.isBatteryOk(batteryLevel)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(PowerManager::class.java).currentThermalStatus
        } else {
            0
        }
        val context = CaptureContext(
            scheduleAllows = scheduleAllows,
            batteryOk = batteryOk
        )

        var next = state

        if (state == CaptureState.PAUSED_USER && now >= pauseUntilMillis) {
            next = CaptureStateMachine.transition(next, CaptureEvent.UserStart, context)
        }

        if (ThermalGuard.shouldDowngrade(thermalStatus)) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ThermalHigh, context)
        } else if (state == CaptureState.PAUSED_THERMAL) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ThermalNormal, context)
        }

        if (!batteryOk) {
            next = CaptureStateMachine.transition(next, CaptureEvent.BatteryLow, context)
        } else if (state == CaptureState.PAUSED_BATTERY) {
            next = CaptureStateMachine.transition(next, CaptureEvent.BatteryRecovered, context)
        }

        if (!scheduleAllows) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ScheduleBlock, context)
        } else if (state == CaptureState.ARMED || state == CaptureState.PAUSED_SCHEDULE) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ScheduleAllow, context)
        }

        if (next == CaptureState.RECORDING &&
            silenceTracker.shouldPause(stationary = isProbablyStationary())
        ) {
            next = CaptureStateMachine.transition(next, CaptureEvent.SilenceTimeout, context)
        }

        applyState(next)
    }

    /**
     * M1 proxy: screen off means the user is probably not walking. Replace with
     * Activity Recognition when M1-T4 is measured on a device.
     */
    private fun isProbablyStationary(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        return !power.isInteractive
    }

    private fun applyState(newState: CaptureState) {
        if (newState == state) {
            updateNotification(newState)
            return
        }

        if (newState.isMicrophoneOpen && !state.isMicrophoneOpen) {
            openMicrophone()
            if (state == CaptureState.ERROR) return
        } else if (!newState.isMicrophoneOpen && state.isMicrophoneOpen) {
            closeMicrophone()
        }

        state = newState
        isRunning = state.isServiceAlive
        updateNotification(newState)

        if (state == CaptureState.ERROR) {
            stopSelf()
        }
    }

    private fun openMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            state = CaptureState.ERROR
            isRunning = false
            updateNotification(CaptureState.ERROR)
            return
        }

        try {
            val vadInstance = SileroVad(assets)
            val source = AudioRecordSource()
            silenceTracker.reset()
            source.start { frame ->
                ringBuffer.write(frame)
                if (vadInstance.isSpeech(frame)) {
                    silenceTracker.onSpeech()
                }
            }
            vad = vadInstance
            audioSource = source
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open microphone", error)
            closeMicrophone()
            state = CaptureState.ERROR
            isRunning = false
            updateNotification(CaptureState.ERROR)
        }
    }

    private fun closeMicrophone() {
        audioSource?.stop()
        audioSource = null
        vad?.release()
        vad = null
        ringBuffer.clear()
    }

    private fun pauseFor(minutes: Int) {
        pauseUntilMillis = System.currentTimeMillis() + minutes * 60_000L
        applyState(CaptureState.PAUSED_USER)
    }

    private fun markLastThirtySeconds() {
        val pcm = ringBuffer.snapshotLast(MARK_SECONDS)
        if (pcm.isEmpty()) {
            updateNotification(CaptureState.ARMED)
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val file = File(
                    filesDir,
                    "marks/mark-${System.currentTimeMillis()}.wav"
                )
                WavWriter.write(file, pcm)
                Log.i(TAG, "Mark saved: ${file.absolutePath}")
            } catch (error: Exception) {
                Log.e(TAG, "Unable to save mark", error)
            }
        }

        notifySafely(
            CaptureNotification.NOTIF_ID,
            CaptureNotification.build(this, getString(R.string.notification_state_marked))
        )
    }

    private fun updateNotification(state: CaptureState) {
        if (!isRunning && state != CaptureState.ERROR) return
        val text = when (state) {
            CaptureState.STOPPED -> getString(R.string.notification_state_armed)
            CaptureState.ARMED -> getString(R.string.notification_state_armed)
            CaptureState.RECORDING -> getString(R.string.notification_state_recording)
            CaptureState.PAUSED_SCHEDULE -> getString(R.string.notification_state_paused_schedule)
            CaptureState.PAUSED_BATTERY -> getString(R.string.notification_state_paused_battery)
            CaptureState.PAUSED_THERMAL -> getString(R.string.notification_state_paused_thermal)
            CaptureState.PAUSED_SILENCE -> getString(R.string.notification_state_paused_silence)
            CaptureState.PAUSED_USER -> getString(R.string.notification_state_paused_user)
            CaptureState.ERROR -> getString(R.string.notification_state_error)
        }
        notifySafely(
            CaptureNotification.NOTIF_ID,
            CaptureNotification.build(this, text)
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun postResumeNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        notifySafely(
            CaptureNotification.RESUME_NOTIF_ID,
            CaptureNotification.buildResumeNotification(this)
        )
    }

    private fun playStartTone() {
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
        tickJob?.cancel()
        tickJob = null
        scheduleJob?.cancel()
        scheduleJob = null
        serviceScope.cancel()
        closeMicrophone()
        toneGenerator?.release()
        toneGenerator = null
        try {
            unregisterReceiver(batteryReceiver)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Battery receiver was not registered", error)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
