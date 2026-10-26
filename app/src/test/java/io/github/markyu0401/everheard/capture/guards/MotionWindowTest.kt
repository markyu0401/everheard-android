package io.github.markyu0401.everheard.capture.guards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MotionWindowTest {

    @Test
    fun stationary_only_after_the_window_without_motion() {
        val window = MotionWindow(windowMs = 5 * 60_000L)
        window.reset(now = 0L)

        assertThat(window.isStationary(4 * 60_000L)).isFalse()
        assertThat(window.isStationary(5 * 60_000L)).isTrue()
    }

    @Test
    fun motion_resets_the_window() {
        val window = MotionWindow(windowMs = 5 * 60_000L)
        window.reset(now = 0L)
        window.onMotion(now = 4 * 60_000L)

        assertThat(window.isStationary(8 * 60_000L)).isFalse()
        assertThat(window.isStationary(9 * 60_000L)).isTrue()
    }

    @Test
    fun never_stationary_before_reset() {
        val window = MotionWindow(windowMs = 1_000L)
        assertThat(window.isStationary(10_000L)).isFalse()
    }
}
