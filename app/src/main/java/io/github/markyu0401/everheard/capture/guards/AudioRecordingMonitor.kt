package io.github.markyu0401.everheard.capture.guards

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/**
 * Detects when another app is capturing audio, or when the framework has
 * silenced our own capture because a higher-priority client took the mic.
 *
 * Android 10+ deliberately allows only privileged clients to capture
 * concurrently with an ordinary app. A voice keyboard, a call, or a meeting app
 * can therefore take the microphone away from Everheard; this monitor lets the
 * service pause visibly instead of writing silence.
 */
class AudioRecordingMonitor(
    context: Context,
    private val ownSessionIdProvider: () -> Int,
    private val onChanged: (Boolean) -> Unit
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var busy = false

    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(
            configs: MutableList<AudioRecordingConfiguration>?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            // getActiveRecordingConfigurations() is authoritative; the callback
            // list can include configurations that are no longer active.
            val active = audioManager.activeRecordingConfigurations
            busy = isMicBusy(active, ownSessionIdProvider())
            onChanged(busy)
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                audioManager.registerAudioRecordingCallback(callback, handler)
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { audioManager.unregisterAudioRecordingCallback(callback) }
        }
    }

    fun isBusy(): Boolean = busy

    fun refresh(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            busy = isMicBusy(
                audioManager.activeRecordingConfigurations,
                ownSessionIdProvider()
            )
        }
        return busy
    }

    companion object {
        /**
         * Busy when another non-silenced client is recording, or when our own
         * client has been silenced by the concurrent-capture policy. Our own
         * client is identified by its AudioRecord session id; the public API
         * does not expose a client UID.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        fun isMicBusy(
            configs: List<AudioRecordingConfiguration>,
            ownSessionId: Int
        ): Boolean {
            val own = configs.firstOrNull { config ->
                ownSessionId != 0 && config.clientAudioSessionId == ownSessionId
            }
            val ownSilenced = own?.isClientSilenced == true
            val otherActive = configs.any { config ->
                config.clientAudioSessionId != ownSessionId && !config.isClientSilenced
            }
            return ownSilenced || otherActive
        }
    }
}
