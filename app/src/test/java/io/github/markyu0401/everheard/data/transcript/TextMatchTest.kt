package io.github.markyu0401.everheard.data.transcript

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextMatchTest {

    @Test
    fun matches_exact_text_case_insensitively() {
        assertThat(TextMatch.matches("眼压", "我记得我今年测的时候，有一个人说我的眼压高。")).isTrue()
        assertThat(TextMatch.matches("TIMELINE", "The timeline risk is the API.")).isTrue()
    }

    @Test
    fun ignores_spaces_and_punctuation() {
        assertThat(TextMatch.matches("air pods", "AirPods Pro 3")).isTrue()
        assertThat(TextMatch.matches("get hub", "放到 getub 上")).isFalse()
    }

    @Test
    fun finds_the_asr_spelling_of_a_product_name() {
        assertThat(TextMatch.matches("AirPods", "airpos pro3")).isTrue()
        assertThat(TextMatch.matches("timeline", "the timelime risk")).isTrue()
    }

    @Test
    fun does_not_fuzz_short_tokens() {
        assertThat(TextMatch.matches("cat", "car")).isFalse()
    }

    @Test
    fun empty_query_matches_nothing() {
        assertThat(TextMatch.matches("   ", "anything")).isFalse()
    }
}
