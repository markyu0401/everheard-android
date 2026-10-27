package io.github.markyu0401.everheard.speaker

import kotlin.math.sqrt

/**
 * Cosine matching for the enrolled voice profile. This is attribution, not
 * authentication: a score above the threshold means "probably me", never
 * "proven identity".
 */
object VoiceMatcher {

    const val DEFAULT_THRESHOLD = 0.72f

    /** Below ~1.5 s, a segment embedding is too noisy to attribute. */
    const val MIN_SPEAKER_SAMPLES = 24_000

    fun normalize(vector: FloatArray): FloatArray? {
        var sumSquares = 0.0
        for (value in vector) {
            sumSquares += value.toDouble() * value
        }
        if (sumSquares <= 1e-12) return null
        val norm = sqrt(sumSquares).toFloat()
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    fun similarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return -1f
        val a = normalize(left) ?: return -1f
        val b = normalize(right) ?: return -1f
        var dot = 0.0f
        for (index in a.indices) {
            dot += a[index] * b[index]
        }
        return dot.coerceIn(-1f, 1f)
    }

    fun isMe(
        embedding: FloatArray,
        profile: VoiceProfile,
        threshold: Float = DEFAULT_THRESHOLD
    ): Boolean = similarity(embedding, profile.embedding) >= threshold
}
