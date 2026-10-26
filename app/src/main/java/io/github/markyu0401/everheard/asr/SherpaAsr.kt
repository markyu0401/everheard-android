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
 * The small model is used for M0/M2: encoder 41 MB, decoder 3.3 MB, joiner
 * 3.1 MB. It lives in assets because sherpa-onnx's Android constructor takes an
 * AssetManager; scripts/fetch_models.sh populates it locally.
 */
class SherpaAsr(
    private val assets: AssetManager,
    private val frameSamples: Int = 320
) : StreamingAsrEngine {

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
                // The 2023-02-16 bilingual model is a first-generation streaming
                // Zipformer; forcing "zipformer2" makes sherpa-onnx abort because
                // the encoder metadata has no query_head_dims. Leave it empty so
                // the runtime detects the model type from the metadata.
                modelType = ""
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
            val session = createSession(request.sampleRate)
            try {
                session.accept(request.pcm16)
                session.finish()
            } finally {
                session.close()
            }
        }

    override fun createSession(sampleRate: Int): StreamingAsrSession =
        SherpaSession(sampleRate)

    private inner class SherpaSession(
        private val sampleRate: Int
    ) : StreamingAsrSession {

        private val stream = recognizer.createStream()
        private val startedAt = SystemClock.elapsedRealtime()
        private val pieces = mutableListOf<String>()
        private val segments = mutableListOf<AsrSegment>()

        private var index = 0
        private var segmentStartSample = 0
        private var finished = false
        private var closed = false
        private var cached: AsrResult? = null

        override fun accept(pcm16: ShortArray) {
            if (closed || finished || pcm16.isEmpty()) return
            var cursor = 0
            while (cursor < pcm16.size) {
                val end = minOf(cursor + frameSamples, pcm16.size)
                val floats = FloatArray(end - cursor) { offset ->
                    pcm16[cursor + offset] / 32768.0f
                }

                stream.acceptWaveform(floats, sampleRate)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                if (recognizer.isEndpoint(stream)) {
                    val text = recognizer.getResult(stream).text.trim()
                    val absoluteEnd = index + end
                    if (text.isNotEmpty()) {
                        pieces += text
                        segments += AsrSegment(
                            text = text,
                            startMs = segmentStartSample * 1000L / sampleRate,
                            endMs = absoluteEnd * 1000L / sampleRate
                        )
                    }
                    recognizer.reset(stream)
                    segmentStartSample = absoluteEnd
                }

                cursor = end
            }
            index += pcm16.size
        }

        override fun partialText(): String {
            if (closed) return pieces.joinToString(" ").trim()
            val tail = recognizer.getResult(stream).text.trim()
            val all = if (tail.isEmpty()) pieces else pieces + tail
            return all.joinToString(" ").trim()
        }

        override fun finish(): AsrResult {
            cached?.let { return it }
            if (closed) {
                return cached ?: AsrResult("", emptyList(), id, 0L)
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
                    startMs = segmentStartSample * 1000L / sampleRate,
                    endMs = index * 1000L / sampleRate
                )
            }

            finished = true
            val result = AsrResult(
                text = pieces.joinToString(" ").trim(),
                segments = segments.toList(),
                model = id,
                latencyMs = SystemClock.elapsedRealtime() - startedAt
            )
            cached = result
            stream.release()
            closed = true
            return result
        }

        override fun close() {
            if (closed) return
            closed = true
            stream.release()
        }
    }

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).use { }
        true
    } catch (_: IOException) {
        false
    }
}
