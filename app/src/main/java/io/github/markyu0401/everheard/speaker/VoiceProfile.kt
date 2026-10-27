package io.github.markyu0401.everheard.speaker

/**
 * An enrolled voice: one or more L2-normalised CAM++ embeddings, not the audio.
 * A second embedding lets the profile cover more than one language or acoustic
 * condition. [samples] is the total PCM used to build the profile.
 */
data class VoiceProfile(
    val embedding: FloatArray,
    val samples: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val alternates: List<FloatArray> = emptyList(),
    val languages: List<String> = emptyList()
) {
    fun allEmbeddings(): List<FloatArray> = listOf(embedding) + alternates

    fun languageAt(index: Int): String =
        languages.getOrNull(index) ?: if (index == 0) "en" else "und"
}
