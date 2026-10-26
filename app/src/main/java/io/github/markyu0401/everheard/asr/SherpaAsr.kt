package io.github.markyu0401.everheard.asr

import android.content.res.AssetManager
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device streaming Zipformer (bilingual zh-en, int8).
 *
 * The small model is used for M0: encoder 41 MB, decoder 3.3 MB, joiner 3.1 MB.
 * It lives in assets because sherpa-onnx's Android constructor takes an
 * AssetManager; scripts/fetch_models.sh populates it locally.
 */
class SherpaAsr(
    private val assets: AssetManager,
    private val frameSamples: Int = 320
) : AsrEngine {

    companion object {
        const val MODEL_ID = "sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-int8"
        private const val DIR = "models/asr-small"
        private const val ENCODER = "$DIR/encoder.int8.onnx"
        private const val DECODER = "$DIR/decoder.int8.onnx"
        private const val JOINER = "$DIR/joiner.int8.onnx"
        private const val TOKENS = "$DIR/tokens.txt"
    }

    override val id: String = MODEL_ID

    private val recognizer: OnlineRecognizer

    init {
        val missing = listOf(ENCODER, DECODER, JOINER, TOKENS).filterNot(::assetExists)
        if (missing.isNotEmpty()) {
            throw AsrModelMissingException(
                "ASR model missing (${missing.joinToString()}). Run scripts/fetch_models.sh."
            )
        }

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(16_000, 80, 0.0f),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = ENCODER,
                    decoder = DECODER,
                    joiner = JOINER
                ),
                tokens = TOKENS,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "zipformer2"
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )
        recognizer = OnlineRecognizer(assets, config)
    }

    override suspend fun transcribe(request: AsrRequest): AsrResult =
        withContext(Dispatchers.Default) {
            require(request.pcm16.isNotEmpty()) { "empty audio" }

            val startedAt = SystemClock.elapsedRealtime()
            val stream = recognizer.createStream()
            val pieces = mutableListOf<String>()
            val segments = mutableListOf<AsrSegment>()
            var segmentStartSample = 0
            var index = 0

            try {
                while (index < request.pcm16.size) {
                    val end = minOf(index + frameSamples, request.pcm16.size)
                    val floats = FloatArray(end - index) { offset ->
                        request.pcm16[index + offset] / 32768.0f
                    }

                    stream.acceptWaveform(floats, request.sampleRate)
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }

                    if (recognizer.isEndpoint(stream)) {
                        val text = recognizer.getResult(stream).text.trim()
                        if (text.isNotEmpty()) {
                            pieces += text
                            segments += AsrSegment(
                                text = text,
                                startMs = segmentStartSample * 1000L / request.sampleRate,
                                endMs = end * 1000L / request.sampleRate
                            )
                        }
                        recognizer.reset(stream)
                        segmentStartSample = end
                    }

                    index = end
                }

                stream.inputFinished()
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val tail = recognizer.getResult(stream).text.trim()
                if (tail.isNotEmpty()) {
                    pieces += tail
                    segments += AsrSegment(
                        text = tail,
                        startMs = segmentStartSample * 1000L / request.sampleRate,
                        endMs = request.pcm16.size * 1000L / request.sampleRate
                    )
                }
            } finally {
                stream.release()
            }

            AsrResult(
                text = pieces.joinToString(" ").trim(),
                segments = segments,
                model = id,
                latencyMs = SystemClock.elapsedRealtime() - startedAt
            )
        }

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).use { }
        true
    } catch (_: IOException) {
        false
    }
}
