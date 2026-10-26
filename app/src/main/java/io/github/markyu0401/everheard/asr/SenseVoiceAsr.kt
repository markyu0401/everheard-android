package io.github.markyu0401.everheard.asr

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Final-pass ASR: SenseVoice small, int8, multilingual (zh/en/ja/ko/yue).
 *
 * SenseVoice is offline and non-autoregressive. It is noticeably more accurate
 * on Mandarin than the small streaming Zipformer, so capture uses it for the
 * durable transcript while the live screen keeps the streaming model for
 * immediate partials. The model is ~228 MB and is loaded lazily on the first
 * speech segment; peak memory is acceptable on a flagship, but low-RAM devices
 * should use the streaming-only path.
 */
class SenseVoiceAsr(
    private val assets: AssetManager,
    private val modelDir: String = "models/sense-voice",
    private val language: String = "auto"
) : AsrEngine {

    companion object {
        const val MODEL_ID = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17-int8"
    }

    override val id: String = MODEL_ID

    private val recognizer: OfflineRecognizer

    init {
        val model = "$modelDir/model.int8.onnx"
        val tokens = "$modelDir/tokens.txt"
        val missing = listOf(model, tokens).filterNot(::assetExists)
        if (missing.isNotEmpty()) {
            throw AsrModelMissingException(
                "SenseVoice model missing (${missing.joinToString()}). Run scripts/fetch_models.sh."
            )
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(16_000, 80, 0.0f),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = model,
                    language = language,
                    useInverseTextNormalization = true
                ),
                tokens = tokens,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        recognizer = OfflineRecognizer(assets, config)
    }

    override suspend fun transcribe(request: AsrRequest): AsrResult =
        withContext(Dispatchers.Default) {
            require(request.pcm16.isNotEmpty()) { "empty audio" }
            val stream = recognizer.createStream()
            try {
                val floats = FloatArray(request.pcm16.size) { index ->
                    request.pcm16[index] / 32768.0f
                }
                stream.acceptWaveform(floats, request.sampleRate)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                AsrResult(
                    text = result.text.trim(),
                    segments = emptyList(),
                    model = id,
                    latencyMs = 0L
                )
            } finally {
                stream.release()
            }
        }

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).use { }
        true
    } catch (_: java.io.IOException) {
        false
    }
}
