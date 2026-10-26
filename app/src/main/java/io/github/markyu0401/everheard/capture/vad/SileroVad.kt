package io.github.markyu0401.everheard.capture.vad

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero VAD v5 through sherpa-onnx.
 *
 * The model expects 512-sample windows at 16 kHz; AudioRecord delivers 320-sample
 * frames. This wrapper accumulates frames and reports the state of the most
 * recently completed window. Silence before the first window returns false.
 */
class SileroVad(
    assets: AssetManager,
    threshold: Float = 0.5f,
    minSpeechSeconds: Float = 0.25f,
    minSilenceSeconds: Float = 0.5f,
    private val windowSize: Int = 512
) {

    private val config = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = "models/silero_vad.onnx",
            threshold = threshold,
            minSilenceDuration = minSilenceSeconds,
            minSpeechDuration = minSpeechSeconds,
            windowSize = windowSize,
            maxSpeechDuration = 30.0f
        ),
        sampleRate = 16_000,
        numThreads = 1,
        provider = "cpu",
        debug = false
    )

    private val vad = Vad(assets, config)
    private val window = FloatArray(windowSize)
    private var offset = 0
    private var lastSpeech = false

    fun isSpeech(frame: ShortArray): Boolean {
        var index = 0
        while (index < frame.size) {
            val copy = minOf(windowSize - offset, frame.size - index)
            for (i in 0 until copy) {
                window[offset + i] = frame[index + i] / 32768.0f
            }
            offset += copy
            index += copy

            if (offset == windowSize) {
                vad.acceptWaveform(window)
                lastSpeech = vad.isSpeechDetected()
                offset = 0
            }
        }
        return lastSpeech
    }

    fun reset() {
        vad.reset()
        offset = 0
        lastSpeech = false
    }

    fun release() {
        vad.release()
    }
}
