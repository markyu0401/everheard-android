package io.github.markyu0401.everheard.capture.guards

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi

/** One active recorder as the framework reports it. */
data class MicClient(
    val sessionId: Int,
    val source: Int,
    val silenced: Boolean
)

/**
 * Result of one microphone-contention check.
 *
 * [ownSilenced] means the framework is delivering silence to Everheard because
 * a higher-priority client (voice input, a call, an assistant) took the mic.
 * [otherActive] means another non-hotword client is receiving audio while our
 * recorder is closed, so opening the mic now would only yield silence.
 * [otherSilenced] is another client that lost the policy race; it is reported
 * for diagnostics and is not contention for us.
 */
data class MicContention(
    val ownSilenced: Boolean = false,
    val otherActive: Boolean = false,
    val otherSilenced: Boolean = false
) {
    val busy: Boolean get() = ownSilenced || otherActive

    companion object {
        // MediaRecorder.AudioSource.HOTWORD is 1999; the constant is hidden from
        // some SDK stubs, so keep the value here.
        const val AUDIO_SOURCE_HOTWORD = 1999

        /**
         * [ignoreOtherActive] covers the short window after our own
         * AudioRecord starts, when some OEMs have not exposed its session id
         * yet: without the grace period our own configuration would look like
         * "another app" and the service would immediately pause itself.
         */
        fun classify(
            clients: List<MicClient>,
            ownSessionId: Int,
            ownSilenced: Boolean,
            ignoreOtherActive: Boolean = false
        ): MicContention {
            val own = clients.firstOrNull { ownSessionId != 0 && it.sessionId == ownSessionId }
            val others = clients.filter { client ->
                (ownSessionId == 0 || client.sessionId != ownSessionId) &&
                    client.source != AUDIO_SOURCE_HOTWORD
            }
            return MicContention(
                ownSilenced = ownSilenced || own?.silenced == true,
                otherActive = !ignoreOtherActive && others.any { !it.silenced },
                otherSilenced = others.any { it.silenced }
            )
        }
    }
}

/**
 * Detects when another app is capturing audio, or when the framework has
 * silenced our own capture because a higher-priority client took the mic.
 *
 * Android 10+ deliberately allows only privileged clients to capture
 * concurrently with an ordinary app. A voice keyboard, a call, or a meeting app
 * can therefore take the microphone away from Everheard; this monitor lets the
 * service pause visibly instead of writing silence.
 *
 * The own-silenced signal comes from our AudioRecord
 * (`getActiveRecordingConfiguration()`), which is more reliable than matching
 * session ids in the global list; the global list is used for other clients.
 */
class AudioRecordingMonitor(
    context: Context,
    private val ownSessionIdProvider: () -> Int,
    private val ownSilencedProvider: () -> Boolean,
    private val ownOpenSinceProvider: () -> Long,
    private val onChanged: (MicContention) -> Unit
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var contention = MicContention()

    private val callback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(
            configs: MutableList<AudioRecordingConfiguration>?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            // getActiveRecordingConfigurations() is authoritative; the callback
            // list can include configurations that are no longer active.
            val next = read()
            contention = next
            onChanged(next)
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

    fun isBusy(): Boolean = contention.busy

    fun contention(): MicContention {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return MicContention()
        val next = read()
        contention = next
        return next
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun read(): MicContention {
        val clients = runCatching {
            audioManager.activeRecordingConfigurations.map { config ->
                MicClient(
                    sessionId = config.clientAudioSessionId,
                    source = config.clientAudioSource,
                    silenced = config.isClientSilenced
                )
            }
        }.getOrDefault(emptyList())

        val openedAt = ownOpenSinceProvider()
        val ownOpen = openedAt > 0L
        val grace = ownOpen && SystemClock.elapsedRealtime() - openedAt < OPEN_GRACE_MS
        return MicContention.classify(
            clients = clients,
            ownSessionId = ownSessionIdProvider(),
            ownSilenced = ownSilencedProvider(),
            ignoreOtherActive = grace
        )
    }

    private companion object {
        const val OPEN_GRACE_MS = 2_000L
    }
}
