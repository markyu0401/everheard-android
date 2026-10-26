package io.github.markyu0401.everheard.speaker

/**
 * Speaker embedding behind an interface so the clustering logic can be tested
 * without the native model.
 */
interface SpeakerEmbedder {
    val id: String
    suspend fun embed(pcm16: ShortArray, sampleRate: Int = 16_000): FloatArray
}

class SpeakerModelMissingException(message: String) : IllegalStateException(message)
