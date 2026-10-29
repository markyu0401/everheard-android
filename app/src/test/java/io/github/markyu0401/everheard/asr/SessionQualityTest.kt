package io.github.markyu0401.everheard.asr

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import org.junit.Test

class SessionQualityTest {

    private fun utterance(startMs: Long, endMs: Long, text: String, speaker: String? = null) =
        TranscriptUtterance(
            id = "u$startMs",
            sessionId = "s",
            startMs = startMs,
            endMs = endMs,
            text = text,
            speaker = speaker
        )

    @Test
    fun rejects_an_empty_session() {
        assertThat(SessionQuality.qualifies(emptyList())).isFalse()
    }

    @Test
    fun rejects_a_single_noise_row() {
        assertThat(
            SessionQuality.qualifies(
                listOf(utterance(0, 1_900, "."))
            )
        ).isFalse()
        assertThat(
            SessionQuality.qualifies(
                listOf(utterance(0, 2_100, "帮我做这个。"))
            )
        ).isFalse()
    }

    @Test
    fun keeps_a_single_long_substantive_sentence() {
        assertThat(
            SessionQuality.qualifies(
                listOf(
                    utterance(
                        0,
                        6_000,
                        "我给你讲一个卖火柴的小女孩的故事怎么样？好啊好啊。"
                    )
                )
            )
        ).isTrue()
    }

    @Test
    fun requires_two_utterances_and_eight_seconds() {
        val short = listOf(
            utterance(0, 2_000, "第一句话。"),
            utterance(2_500, 4_500, "第二句话。")
        )
        assertThat(SessionQuality.qualifies(short)).isFalse()

        val long = listOf(
            utterance(0, 4_500, "第一句比较长的话。"),
            utterance(5_000, 9_000, "第二句比较长的话。")
        )
        assertThat(SessionQuality.qualifies(long)).isTrue()
    }

    @Test
    fun title_prefers_the_longest_voiceprint_confirmed_row() {
        val utterances = listOf(
            utterance(0, 2_000, "Yeah."),
            utterance(2_000, 6_000, "这是一个很长而且有意义的句子。", speaker = "me"),
            utterance(6_000, 8_000, "短句。", speaker = "other")
        )

        assertThat(SessionQuality.titleFor(utterances))
            .isEqualTo("这是一个很长而且有意义的句子。")
    }
}
