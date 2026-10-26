package io.github.markyu0401.everheard.asr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechSegmenterTest {

    private fun frame(ms: Int, speech: Boolean): ShortArray {
        val samples = ms * 16
        return ShortArray(samples) { if (speech) 1_000 else 0 }
    }

    @Test
    fun emits_a_segment_after_speech_and_silence() {
        val segmenter = SpeechSegmenter(
            minSpeechMs = 200,
            endSilenceMs = 150,
            maxSegmentMs = 2_000
        )

        assertThat(segmenter.accept(frame(100, speech = false), false)).isEmpty()
        assertThat(segmenter.accept(frame(300, speech = true), true)).isEmpty()
        assertThat(segmenter.accept(frame(100, speech = false), false)).isEmpty()
        val segments = segmenter.accept(frame(100, speech = false), false)

        assertThat(segments).hasSize(1)
        assertThat(segments[0].startMs).isEqualTo(100L)
        assertThat(segments[0].pcm16.size).isGreaterThan(0)
    }

    @Test
    fun drops_short_noise() {
        val segmenter = SpeechSegmenter(
            minSpeechMs = 500,
            endSilenceMs = 100,
            maxSegmentMs = 2_000
        )

        segmenter.accept(frame(100, speech = true), true)
        val segments = segmenter.accept(frame(100, speech = false), false)

        assertThat(segments).isEmpty()
    }

    @Test
    fun splits_a_long_segment_at_the_maximum() {
        val segmenter = SpeechSegmenter(
            minSpeechMs = 100,
            endSilenceMs = 100,
            maxSegmentMs = 500
        )

        var segments = emptyList<SpeechSegmenter.Segment>()
        repeat(10) {
            segments = segmenter.accept(frame(100, speech = true), true)
        }

        assertThat(segments).hasSize(1)
    }

    @Test
    fun flush_emits_the_open_segment() {
        val segmenter = SpeechSegmenter(
            minSpeechMs = 100,
            endSilenceMs = 1_000,
            maxSegmentMs = 2_000
        )

        segmenter.accept(frame(300, speech = true), true)
        val segment = segmenter.flush()

        assertThat(segment).isNotNull()
        assertThat(segment!!.pcm16.size).isGreaterThan(0)
    }
}
