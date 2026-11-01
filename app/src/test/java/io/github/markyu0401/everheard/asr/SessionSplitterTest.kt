package io.github.markyu0401.everheard.asr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionSplitterTest {

    @Test
    fun splits_after_five_minutes_of_silence() {
        val lastEnd = 10_000L
        assertThat(SessionSplitter.shouldSplit(lastEnd, lastEnd + SessionSplitter.GAP_MS)).isTrue()
    }

    @Test
    fun keeps_short_pauses_inside_one_session() {
        val lastEnd = 10_000L
        assertThat(SessionSplitter.shouldSplit(lastEnd, lastEnd + SessionSplitter.GAP_MS - 1)).isFalse()
    }

    @Test
    fun first_utterance_never_splits() {
        assertThat(SessionSplitter.shouldSplit(null, 10_000L)).isFalse()
    }
}
