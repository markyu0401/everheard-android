package io.github.markyu0401.everheard.capture.guards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MicContentionTest {

    private val hotword = MicContention.AUDIO_SOURCE_HOTWORD

    @Test
    fun our_own_silenced_capture_is_busy() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 42, source = 6, silenced = true)),
            ownSessionId = 42,
            ownSilenced = true
        )
        assertThat(contention.ownSilenced).isTrue()
        assertThat(contention.busy).isTrue()
    }

    @Test
    fun another_active_recorder_is_busy() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 7, source = 6, silenced = false)),
            ownSessionId = 0,
            ownSilenced = false
        )
        assertThat(contention.otherActive).isTrue()
        assertThat(contention.busy).isTrue()
    }

    @Test
    fun a_silenced_other_client_is_not_contention() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 7, source = 6, silenced = true)),
            ownSessionId = 0,
            ownSilenced = false
        )
        assertThat(contention.otherSilenced).isTrue()
        assertThat(contention.busy).isFalse()
    }

    @Test
    fun always_on_hotword_clients_are_ignored() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 9, source = hotword, silenced = false)),
            ownSessionId = 0,
            ownSilenced = false
        )
        assertThat(contention.busy).isFalse()
    }

    @Test
    fun our_own_session_is_not_counted_as_another_app() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 42, source = 6, silenced = false)),
            ownSessionId = 42,
            ownSilenced = false
        )
        assertThat(contention.otherActive).isFalse()
        assertThat(contention.busy).isFalse()
    }

    @Test
    fun the_open_grace_period_suppresses_a_self_reported_other_client() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 0, source = 6, silenced = false)),
            ownSessionId = 0,
            ownSilenced = false,
            ignoreOtherActive = true
        )
        assertThat(contention.otherActive).isFalse()
        assertThat(contention.busy).isFalse()
    }

    @Test
    fun own_silenced_still_wins_during_the_open_grace_period() {
        val contention = MicContention.classify(
            clients = listOf(MicClient(sessionId = 0, source = 6, silenced = true)),
            ownSessionId = 0,
            ownSilenced = true,
            ignoreOtherActive = true
        )
        assertThat(contention.busy).isTrue()
    }
}
