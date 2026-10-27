package io.github.markyu0401.everheard.capture.guards

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper

/**
 * Reports whether an app is playing audible media or communication audio.
 * Android does not expose the owning package on AudioPlaybackConfiguration, so
 * the service combines this signal with ForegroundAppMonitor's package.
 */
class AudioPlaybackMonitor(
    context: Context,
    private val onChanged: (Boolean) -> Unit
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(
            configs: MutableList<AudioPlaybackConfiguration>?
        ) {
            // getActivePlaybackConfigurations() is authoritative; the callback
            // list can include configurations that are no longer audible.
            onChanged(hasAudiblePlayback(audioManager.activePlaybackConfigurations))
        }
    }

    fun start() {
        runCatching {
            audioManager.registerAudioPlaybackCallback(callback, handler)
        }
    }

    fun stop() {
        runCatching { audioManager.unregisterAudioPlaybackCallback(callback) }
    }

    fun refresh() {
        onChanged(hasAudiblePlayback(audioManager.activePlaybackConfigurations))
    }

    fun isPlaying(): Boolean =
        hasAudiblePlayback(audioManager.activePlaybackConfigurations)

    companion object {
        private val AUDIBLE_USAGES = setOf(
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
        )

        fun hasAudiblePlayback(configs: List<AudioPlaybackConfiguration>): Boolean =
            configs.any { config ->
                config.audioAttributes.usage in AUDIBLE_USAGES
            }
    }
}
