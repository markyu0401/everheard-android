package io.github.markyu0401.everheard.asr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptQualityTest {

    @Test
    fun rejects_punctuation_only_hallucinations() {
        assertThat(TranscriptQuality.isAcceptable(".", 1_900)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("。", 2_000)).isFalse()
        assertThat(TranscriptQuality.isAcceptable(". . . . 。 .", 98_000)).isFalse()
    }

    @Test
    fun rejects_short_filler_tokens() {
        assertThat(TranscriptQuality.isAcceptable("嗯。", 1_900)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("嗯嘿。", 2_600)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("B。", 2_400)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("The.", 4_600)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("Yeah.", 2_000)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("Okay.", 2_000)).isFalse()
    }

    @Test
    fun rejects_text_that_is_too_sparse_for_its_duration() {
        // Two characters over five seconds is a stray token, not speech.
        assertThat(TranscriptQuality.isAcceptable("好吃。", 5_100)).isFalse()
        assertThat(TranscriptQuality.isAcceptable("空。", 2_500)).isFalse()
    }

    @Test
    fun rejects_noise_with_a_low_speech_ratio() {
        assertThat(
            TranscriptQuality.isAcceptable(
                "这是一个真实句子。",
                4_000,
                speechRatio = 0.2f
            )
        ).isFalse()
    }

    @Test
    fun accepts_real_sentences() {
        assertThat(
            TranscriptQuality.isAcceptable("你看一下。", 2_400, speechRatio = 0.8f)
        ).isTrue()
        assertThat(
            TranscriptQuality.isAcceptable(
                "邮件。是hers那个团队给发的。",
                5_500,
                speechRatio = 0.7f
            )
        ).isTrue()
        assertThat(
            TranscriptQuality.isAcceptable(
                "You have 10 minutes left, you should review your code.",
                8_000,
                speechRatio = 0.9f
            )
        ).isTrue()
    }

    @Test
    fun keeps_a_real_sentence_that_starts_with_a_filler_word() {
        assertThat(
            TranscriptQuality.isAcceptable(
                "Okay, let's start the meeting.",
                4_000,
                speechRatio = 0.9f
            )
        ).isTrue()
    }

    @Test
    fun rejects_too_short_segments() {
        assertThat(TranscriptQuality.isAcceptable("好的", 400)).isFalse()
    }
}
