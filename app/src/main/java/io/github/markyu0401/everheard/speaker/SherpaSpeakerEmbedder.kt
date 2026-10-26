package io.github.markyu0401.everheard.speaker

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * CAM++ bilingual zh-en speaker embedding (27 MB) through sherpa-onnx.
 *
 * Used for enrollment ("me") and online clustering. The embedding is returned
 * unnormalised; OnlineCluster normalises before cosine comparison.
 */
class SherpaSpeakerEmbedder(
    private val assets: AssetManager
) : SpeakerEmbedder {

    companion object {
        const val MODEL_ID = "3dspeaker-campplus-zh-en-16k"
        private const val MODEL = "models/speaker/campplus_zh_en.onnx"
    }

    override val id: String = MODEL_ID

    private val extractor: SpeakerEmbeddingExtractor

    init {
        if (!assetExists(MODEL)) {
            throw SpeakerModelMissingException(
                "Speaker model missing ($MODEL). Run scripts/fetch_models.sh."
            )
        }
        val config = SpeakerEmbeddingExtractorConfig(
            model = MODEL,
            numThreads = 1,
            debug = false,
            provider = "cpu"
        )
        extractor = SpeakerEmbeddingExtractor(assets, config)
    }

    override suspend fun embed(pcm16: ShortArray, sampleRate: Int): FloatArray =
        withContext(Dispatchers.Default) {
            require(pcm16.isNotEmpty()) { "empty audio" }
            val stream = extractor.createStream()
            try {
                val floats = FloatArray(pcm16.size) { index ->
                    pcm16[index] / 32768.0f
                }
                stream.acceptWaveform(floats, sampleRate)
                stream.inputFinished()
                while (!extractor.isReady(stream)) {
                    delay(5)
                }
                extractor.compute(stream)
            } finally {
                stream.release()
            }
        }

    fun dimension(): Int = extractor.dim()

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).use { }
        true
    } catch (_: IOException) {
        false
    }
}
