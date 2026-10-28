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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.asr.CaptureTranscriber
import io.github.markyu0401.everheard.asr.SpeechSegmenter
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.audio.RingBuffer
import io.github.markyu0401.everheard.capture.guards.AudioPlaybackMonitor
import io.github.markyu0401.everheard.capture.guards.AudioRecordingMonitor
import io.github.markyu0401.everheard.capture.guards.ForegroundAppMonitor
import io.github.markyu0401.everheard.capture.guards.MicContention
import io.github.markyu0401.everheard.capture.guards.MotionDetector
import io.github.markyu0401.everheard.capture.guards.SilenceTracker
import io.github.markyu0401.everheard.capture.schedule.ScheduleEvaluator
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.capture.vad.SileroVad
import io.github.markyu0401.everheard.data.settings.captureStateStore
import io.github.markyu0401.everheard.data.settings.excludedAppsStore
import io.github.markyu0401.everheard.data.settings.scheduleStore
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
 * One long-lived armed service. Schedule, battery, thermal, microphone and
 * silence rules gate AudioRecord; the service itself is never stopped by a
 * schedule change, because Android refuses to create a microphone foreground
 * service from the background.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "io.github.markyu0401.everheard.action.START"
        const val ACTION_STOP = "io.github.markyu0401.everheard.action.STOP"
        const val ACTION_PAUSE_15 = "io.github.markyu0401.everheard.action.PAUSE_15"
        const val ACTION_PAUSE_60 = "io.github.markyu0401.everheard.action.PAUSE_60"
        const val ACTION_PAUSE_TOMORROW = "io.github.markyu0401.everheard.action.PAUSE_TOMORROW"
        const val ACTION_MARK = "io.github.markyu0401.everheard.action.MARK"

        private const val TAG = "CaptureService"
        private const val TICK_MS = 30_000L
        private const val MARK_SECONDS = 30
        private const val MIC_ALERT_DEBOUNCE_MS = 10_000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateStore by lazy { captureStateStore() }
    private var tickJob: Job? = null
    private var scheduleJob: Job? = null
    private var scheduleRules: List<ScheduleRule> = ScheduleEvaluator.defaultRules()

    private var state: CaptureState = CaptureState.STOPPED
    private var pauseUntilMillis: Long = 0L
    private var batteryLevel: Int = 100
    private var charging: Boolean = false

    private var audioSource: AudioRecordSource? = null
    private var vad: SileroVad? = null
    private var motionDetector: MotionDetector? = null
    private var segmenter: SpeechSegmenter? = null
    private var transcriber: CaptureTranscriber? = null
    private var recordingStartedAt: Long = 0L
    private val ringBuffer = RingBuffer(capacitySamples = 60 * 16_000)
    private val silenceTracker = SilenceTracker(clock = { SystemClock.elapsedRealtime() })

    private var toneGenerator: ToneGenerator? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val excludedStore by lazy { excludedAppsStore() }
    private var playbackMonitor: AudioPlaybackMonitor? = null
    private var recordingMonitor: AudioRecordingMonitor? = null
    private var foregroundAppMonitor: ForegroundAppMonitor? = null

    @Volatile
    private var audioPlaying: Boolean = false

    @Volatile
    private var micContention: MicContention = MicContention()

    /**
     * True only while a recording was actually interrupted by another app.
     * Drives the notification copy ("taken" vs "waiting") and the alert.
     */
    @Volatile
    private var micInterrupted: Boolean = false

    private var pendingMotion: Boolean = false
    private var pendingUserPresent: Boolean = false
    private var silenceMotion: MotionDetector? = null
    private var lastMicAlertAt: Long = 0L
    private var lastNotificationKey: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel) ?: batteryLevel
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            if (plugged >= 0) charging = plugged != 0
            evaluate()
        }
    }

    /**
     * A silence pause closes the microphone and therefore stops the recording
     * motion detector. This receiver is the cheap way back: unlocking the phone
     * is a user-present signal, so the service can resume instead of staying
     * paused until the app is opened.
     */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            pendingUserPresent = true
            mainHandler.post { evaluate() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CaptureNotification.createChannels(this)
        foregroundAppMonitor = ForegroundAppMonitor(this)
        playbackMonitor = AudioPlaybackMonitor(this) { playing ->
            audioPlaying = playing
            mainHandler.post { evaluate() }
        }.also { it.start() }
        recordingMonitor = AudioRecordingMonitor(
            this,
            ownSessionIdProvider = { audioSource?.sessionId ?: 0 },
            ownSilencedProvider = { audioSource?.isSilenced == true },
            ownOpenSinceProvider = { recordingStartedAt }
        ) { contention ->
            if (contention != micContention) {
                Log.i(
                    TAG,
                    "mic contention ownSilenced=${contention.ownSilenced} " +
                        "otherActive=${contention.otherActive} " +
                        "otherSilenced=${contention.otherSilenced}"
                )
            }
            micContention = contention
            mainHandler.post { evaluate() }
        }.also { it.start() }
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scheduleJob = serviceScope.launch {
            scheduleStore().rules.collect { rules ->
                scheduleRules = rules
                evaluate()
            }
        }
        serviceScope.launch {
            stateStore.pauseUntilMillis.collect { millis ->
                pauseUntilMillis = millis
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
            ACTION_PAUSE_TOMORROW -> {
                pauseUntilTomorrow()
                return START_STICKY
            }
            ACTION_MARK -> {
                markLastThirtySeconds()
                return START_STICKY
            }
            else -> startCapture(clearPause = intent?.action == ACTION_START)
        }
        return START_STICKY
    }

    private fun startCapture(clearPause: Boolean) {
        if (clearPause) {
            pauseUntilMillis = 0L
            serviceScope.launch { stateStore.clearPauseUntil() }
        }
        // A duplicate START (tile + orb, or a sticky restart racing the UI)
        // must not close and reopen the microphone or replay the start tone.
        if (isRunning) {
            evaluate()
            return
        }
        val notification = CaptureNotification.build(
            this,
            getString(R.string.notification_state_armed)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

        // A previous failed background start may have left the "tap to resume"
        // notification behind; clear it now that capture is actually running.
        NotificationManagerCompat.from(this).cancel(CaptureNotification.RESUME_NOTIF_ID)

        state = CaptureState.ARMED
        isRunning = true
        micInterrupted = false
        pendingMotion = false
        pendingUserPresent = false
        lastNotificationKey = null
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
        // Battery, schedule, pause and monitor callbacks all call this from
        // different threads. Serialize every transition on the main thread so
        // two evaluations cannot both see "mic closed" and open two records.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { evaluate() }
            return
        }
        if (state == CaptureState.STOPPED || state == CaptureState.ERROR) return

        val now = System.currentTimeMillis()
        val scheduleAllows = ScheduleEvaluator.isAllowed(
            scheduleRules,
            LocalDateTime.now()
        )
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(PowerManager::class.java).currentThermalStatus
        } else {
            0
        }
        val contention = recordingMonitor?.contention() ?: micContention
        micContention = contention
        val audioPlayingNow = playbackMonitor?.isPlaying() ?: audioPlaying
        val excludedPlaying = isExcludedAppPlaying(audioPlayingNow)

        val motion = pendingMotion
        pendingMotion = false
        val unlocked = pendingUserPresent
        pendingUserPresent = false
        if (motion || unlocked) silenceTracker.onMotion()

        val silenceExpired = state == CaptureState.RECORDING &&
            silenceTracker.shouldPause(stationary = isProbablyStationary())

        val decision = CaptureEvaluator.evaluate(
            state,
            CaptureInputs(
                nowMillis = now,
                pauseUntilMillis = pauseUntilMillis,
                batteryLevel = batteryLevel,
                charging = charging,
                thermalStatus = thermalStatus,
                scheduleAllows = scheduleAllows,
                micContention = contention,
                excludedAppPlaying = excludedPlaying,
                silenceExpired = silenceExpired,
                motionDetected = motion,
                screenUnlocked = unlocked
            )
        )

        if (decision.micInterrupted) {
            micInterrupted = true
            alertMicBusy()
        }
        applyState(decision.state)
    }

    private fun isExcludedAppPlaying(playing: Boolean): Boolean {
        if (!playing) return false
        val excluded = excludedStore.load()
        if (excluded.isEmpty()) return false
        val packageName = foregroundAppMonitor?.currentPackage() ?: return false
        return packageName in excluded
    }

    /**
     * Screen-off is the fallback when the device has no accelerometer. With a
     * sensor, "stationary" means no significant movement for five minutes.
     */
    private fun isProbablyStationary(): Boolean {
        motionDetector?.let { detector ->
            if (detector.hasSensor()) return detector.isStationary()
        }
        val power = getSystemService(PowerManager::class.java)
        return !power.isInteractive
    }

    private fun applyState(newState: CaptureState) {
        if (newState == state) {
            updateNotification(newState)
            return
        }

        val oldState = state
        val wasOpen = oldState.isMicrophoneOpen
        if (newState.isMicrophoneOpen && !wasOpen) {
            if (!openMicrophone()) {
                enterError()
                return
            }
        } else if (!newState.isMicrophoneOpen && wasOpen) {
            closeMicrophone()
        }

        state = newState
        isRunning = newState.isServiceAlive
        CaptureController.updateState(newState)
        serviceScope.launch { stateStore.setLastState(newState.name) }

        if (newState == CaptureState.RECORDING) {
            micInterrupted = false
            NotificationManagerCompat.from(this).cancel(CaptureNotification.ALERT_NOTIF_ID)
        }
        if (newState == CaptureState.PAUSED_SILENCE) {
            startSilenceMotion()
        } else {
            stopSilenceMotion()
        }

        Log.i(
            TAG,
            "state $oldState -> $newState " +
                "mic=${micContention.ownSilenced}/${micContention.otherActive} " +
                "battery=$batteryLevel charging=$charging"
        )
        updateNotification(newState)

        if (newState == CaptureState.ERROR) {
            stopSelf()
        }
    }

    private fun enterError() {
        Log.e(TAG, "Capture entered ERROR; microphone could not be opened")
        state = CaptureState.ERROR
        isRunning = false
        stopSilenceMotion()
        CaptureController.updateState(CaptureState.ERROR)
        serviceScope.launch { stateStore.setLastState(CaptureState.ERROR.name) }
        // The foreground notification disappears with the service, so leave a
        // separate high-priority notification that survives stopSelf().
        notifySafely(
            CaptureNotification.RESUME_NOTIF_ID,
            CaptureNotification.buildErrorNotification(this)
        )
        stopSelf()
    }

    private fun openMicrophone(): Boolean {
        Log.i(TAG, "openMicrophone state=$state source=${audioSource?.hashCode()}")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO is not granted")
            return false
        }

        try {
            // A duplicate start command must not leave the previous AudioRecord
            // alive; otherwise the mic-busy guard sees our own second session.
            if (audioSource != null) {
                closeMicrophone()
            }
            val vadInstance = SileroVad(assets)
            val source = AudioRecordSource()
            val segmenterInstance = SpeechSegmenter()
            val transcriptStore = EncryptedTranscriptStore(
                File(filesDir, "transcripts.bin")
            )
            val voiceProfileStore = VoiceProfileStore(
                File(filesDir, "voice_profile.bin")
            )
            val transcriberInstance = CaptureTranscriber(
                context = this,
                store = transcriptStore,
                voiceProfileStore = voiceProfileStore
            )
            silenceTracker.reset()
            vad = vadInstance
            audioSource = source
            segmenter = segmenterInstance
            transcriber = transcriberInstance
            motionDetector = MotionDetector(this).also { it.start() }
            recordingStartedAt = SystemClock.elapsedRealtime()
            transcriberInstance.start()
            source.start { frame ->
                ringBuffer.write(frame)
                val speech = vadInstance.isSpeech(frame)
                if (speech) {
                    silenceTracker.onSpeech()
                }
                segmenterInstance.accept(frame, speech).forEach { segment ->
                    transcriberInstance.submit(segment)
                }
            }
            return true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open microphone", error)
            closeMicrophone()
            return false
        }
    }

    private fun closeMicrophone() {
        Log.i(TAG, "closeMicrophone state=$state source=${audioSource?.hashCode()}")
        audioSource?.stop()
        audioSource = null
        segmenter?.flush()?.let { segment ->
            transcriber?.submit(segment)
        }
        segmenter = null
        transcriber?.stop()
        transcriber = null
        vad?.release()
        vad = null
        motionDetector?.stop()
        motionDetector = null
        recordingStartedAt = 0L
        ringBuffer.clear()
    }

    private fun startSilenceMotion() {
        if (silenceMotion != null) return
        silenceMotion = MotionDetector(this) {
            pendingMotion = true
            mainHandler.post { evaluate() }
        }.also { it.start() }
    }

    private fun stopSilenceMotion() {
        silenceMotion?.stop()
        silenceMotion = null
    }

    private fun pauseFor(minutes: Int) {
        if (!state.isServiceAlive || state == CaptureState.ERROR) return
        pauseUntilMillis = System.currentTimeMillis() + minutes * 60_000L
        serviceScope.launch { stateStore.setPauseUntil(pauseUntilMillis) }
        applyState(CaptureState.PAUSED_USER)
    }

    private fun pauseUntilTomorrow() {
        if (!state.isServiceAlive || state == CaptureState.ERROR) return
        pauseUntilMillis = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        serviceScope.launch { stateStore.setPauseUntil(pauseUntilMillis) }
        applyState(CaptureState.PAUSED_USER)
    }

    private fun markLastThirtySeconds() {
        if (state != CaptureState.RECORDING) {
            updateNotification(
                state,
                detailOverride = getString(R.string.notification_nothing_to_mark)
            )
            return
        }
        val pcm = ringBuffer.snapshotLast(MARK_SECONDS)
        if (pcm.isEmpty()) {
            updateNotification(
                state,
                detailOverride = getString(R.string.notification_nothing_to_mark)
            )
            return
        }

        val endMs = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
        val startMs = (endMs - MARK_SECONDS * 1000L).coerceAtLeast(0L)
        transcriber?.submit(
            SpeechSegmenter.Segment(
                pcm16 = pcm,
                startMs = startMs,
                endMs = endMs
            )
        )

        updateNotification(
            state,
            detailOverride = getString(R.string.notification_marked_detail)
        )
    }

    private fun alertMicBusy() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMicAlertAt < MIC_ALERT_DEBOUNCE_MS) return
        lastMicAlertAt = now
        Log.i(
            TAG,
            "mic taken while recording " +
                "(ownSilenced=${micContention.ownSilenced} " +
                "otherActive=${micContention.otherActive})"
        )
        notifySafely(
            CaptureNotification.ALERT_NOTIF_ID,
            CaptureNotification.buildMicAlert(this)
        )
    }

    private fun stateText(state: CaptureState): String = when (state) {
        CaptureState.STOPPED,
        CaptureState.ARMED -> getString(R.string.notification_state_armed)
        CaptureState.RECORDING -> getString(R.string.notification_state_recording)
        CaptureState.PAUSED_SCHEDULE -> getString(R.string.notification_state_paused_schedule)
        CaptureState.PAUSED_BATTERY -> getString(R.string.notification_state_paused_battery)
        CaptureState.PAUSED_THERMAL -> getString(R.string.notification_state_paused_thermal)
        CaptureState.PAUSED_SILENCE -> getString(R.string.notification_state_paused_silence)
        CaptureState.PAUSED_USER -> getString(R.string.notification_state_paused_user)
        CaptureState.PAUSED_APP_AUDIO -> getString(R.string.notification_state_paused_app_audio)
        CaptureState.PAUSED_MIC_BUSY -> getString(R.string.notification_state_paused_mic_busy)
        CaptureState.ERROR -> getString(R.string.notification_state_error)
    }

    private fun updateNotification(state: CaptureState, detailOverride: String? = null) {
        if (!isRunning && state != CaptureState.ERROR) return
        val text = when {
            state == CaptureState.PAUSED_MIC_BUSY && micInterrupted ->
                getString(R.string.notification_state_paused_mic_busy)
            state == CaptureState.PAUSED_MIC_BUSY ->
                getString(R.string.notification_state_waiting_mic)
            else -> stateText(state)
        }
        val detail = detailOverride ?: when {
            state.isMicrophoneOpen && recordingStartedAt > 0L -> {
                val minutes = (SystemClock.elapsedRealtime() - recordingStartedAt) / 60_000L
                getString(R.string.notification_detail_recording, minutes, batteryLevel)
            }
            state == CaptureState.PAUSED_MIC_BUSY && micInterrupted ->
                getString(R.string.notification_detail_mic_taken)
            state == CaptureState.PAUSED_MIC_BUSY ->
                getString(R.string.notification_detail_waiting_mic)
            else -> getString(R.string.notification_detail_battery, batteryLevel)
        }
        val key = "$text|$detail"
        // The tick runs every 30 s; reposting an unchanged notification is
        // needless work and makes the shade flicker on some ROMs.
        if (detailOverride == null && key == lastNotificationKey) return
        lastNotificationKey = key
        notifySafely(
            CaptureNotification.NOTIF_ID,
            CaptureNotification.build(this, text, detail)
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
        CaptureController.updateState(CaptureState.STOPPED)
        tickJob?.cancel()
        tickJob = null
        scheduleJob?.cancel()
        scheduleJob = null
        serviceScope.cancel()
        closeMicrophone()
        stopSilenceMotion()
        playbackMonitor?.stop()
        playbackMonitor = null
        recordingMonitor?.stop()
        recordingMonitor = null
        foregroundAppMonitor = null
        NotificationManagerCompat.from(this).cancel(CaptureNotification.ALERT_NOTIF_ID)
        toneGenerator?.release()
        toneGenerator = null
        try {
            unregisterReceiver(batteryReceiver)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Battery receiver was not registered", error)
        }
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "User-present receiver was not registered", error)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
