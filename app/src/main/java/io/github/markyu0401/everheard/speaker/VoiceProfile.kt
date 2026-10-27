package io.github.markyu0401.everheard.speaker

/**
 * An enrolled voice: one L2-normalised CAM++ embedding, not the audio.
 * [samples] is the number of PCM samples used to build it.
 */
data class VoiceProfile(
    val embedding: FloatArray,
    val samples: Int,
    val createdAt: Long,
    val updatedAt: Long
)
