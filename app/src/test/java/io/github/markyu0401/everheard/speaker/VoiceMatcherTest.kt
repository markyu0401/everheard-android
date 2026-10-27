package io.github.markyu0401.everheard.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceMatcherTest {

    @Test
    fun similar_vectors_score_above_the_default_threshold() {
        val a = floatArrayOf(1f, 0.1f, 0f)
        val b = floatArrayOf(0.95f, 0.05f, 0f)

        assertThat(VoiceMatcher.similarity(a, b)).isGreaterThan(VoiceMatcher.DEFAULT_THRESHOLD)
    }

    @Test
    fun orthogonal_vectors_do_not_match() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)

        assertThat(VoiceMatcher.similarity(a, b)).isEqualTo(0f)
        assertThat(VoiceMatcher.isMe(b, profile(a))).isFalse()
    }

    @Test
    fun normalisation_rejects_a_zero_vector() {
        assertThat(VoiceMatcher.normalize(floatArrayOf(0f, 0f))).isNull()
    }

    private fun profile(embedding: FloatArray) = VoiceProfile(
        embedding = embedding,
        samples = 16_000,
        createdAt = 1L,
        updatedAt = 1L
    )
}
