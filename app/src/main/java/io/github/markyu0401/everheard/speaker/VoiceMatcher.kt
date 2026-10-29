package io.github.markyu0401.everheard.speaker

import kotlin.math.sqrt

/**
 * Cosine matching for the enrolled voice profile. This is attribution, not
 * authentication: a score above the threshold means "probably me", never
 * "proven identity".
 */
object VoiceMatcher {

    /**
     * Field calibration on the user's own voice (Samsung S26, phone on the desk,
     * spontaneous speech) gave 0.44 / 0.63 / 0.67 / 0.71 for the same speaker in
     * one session. 0.72 fired on 2 of 633 segments; 0.58 catches the confirmed
     * rows without letting CAM++ cross-speaker scores (typically < 0.4) through.
     */
    const val ME_THRESHOLD = 0.58f

    /** Below this the voice is clearly someone else, not merely ambiguous. */
    const val OTHER_THRESHOLD = 0.30f

    const val DEFAULT_THRESHOLD = ME_THRESHOLD

    /** Below ~1.5 s, a segment embedding is too noisy to attribute. */
    const val MIN_SPEAKER_SAMPLES = 24_000

    /**
     * Three-way attribution. `null` means "cannot tell" and must not be treated
     * as the user; only `me` may become the user's commitment downstream.
     */
    fun classify(embedding: FloatArray, profile: VoiceProfile): String? {
        val score = bestSimilarity(embedding, profile)
        return when {
            score >= ME_THRESHOLD -> "me"
            score < OTHER_THRESHOLD -> "other"
            else -> null
        }
    }

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
    ): Boolean = bestSimilarity(embedding, profile) >= threshold

    fun bestSimilarity(embedding: FloatArray, profile: VoiceProfile): Float =
        profile.allEmbeddings().maxOfOrNull { similarity(embedding, it) } ?: -1f
}
