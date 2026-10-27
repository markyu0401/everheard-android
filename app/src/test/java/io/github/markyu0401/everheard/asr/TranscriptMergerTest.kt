package io.github.markyu0401.everheard.asr

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class TranscriptMergerTest {

    private fun utterance(
        startMs: Long,
        endMs: Long,
        text: String
    ) = TranscriptUtterance(
        id = "u",
        sessionId = "s",
        startMs = startMs,
        endMs = endMs,
        text = text
    )

    @Test
    fun merges_a_short_pause_between_chinese_segments() {
        val first = utterance(0, 2_000, "春眠不觉晓，")
        val second = utterance(2_800, 4_500, "处处闻啼鸟。")
        assertThat(TranscriptMerger.shouldMerge(first, second.startMs)).isTrue()
        val merged = TranscriptMerger.merge(first, second)
        assertThat(merged.text).isEqualTo("春眠不觉晓，处处闻啼鸟。")
        assertThat(merged.startMs).isEqualTo(0)
        assertThat(merged.endMs).isEqualTo(4_500)
    }

    @Test
    fun does_not_merge_a_long_pause() {
        val first = utterance(0, 2_000, "First sentence.")
        val second = utterance(4_000, 5_000, "Second sentence.")
        assertThat(TranscriptMerger.shouldMerge(first, second.startMs)).isFalse()
    }

    @Test
    fun does_not_merge_across_different_speakers() {
        val first = utterance(0, 2_000, "First sentence.").copy(speaker = "me")
        val second = utterance(2_500, 3_500, "Second sentence.").copy(speaker = null)
        assertThat(
            TranscriptMerger.shouldMerge(first, second.startMs, nextSpeaker = second.speaker)
        ).isFalse()
    }

    @Test
    fun joins_english_with_a_space_and_chinese_without_one() {
        assertThat(TranscriptMerger.joinText("Hello", "world")).isEqualTo("Hello world")
        assertThat(TranscriptMerger.joinText("你好", "世界")).isEqualTo("你好世界")
    }
}
