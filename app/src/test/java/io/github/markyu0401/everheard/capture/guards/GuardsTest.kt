package io.github.markyu0401.everheard.capture.guards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuardsTest {

    @Test
    fun battery_pauses_below_fifteen_and_resumes_at_thirty() {
        assertThat(BatteryGuard.shouldPause(14)).isTrue()
        assertThat(BatteryGuard.shouldPause(15)).isFalse()
        assertThat(BatteryGuard.isBatteryOk(15)).isTrue()
        assertThat(BatteryGuard.canResume(29)).isFalse()
        assertThat(BatteryGuard.canResume(30)).isTrue()
        assertThat(BatteryGuard.canResume(16)).isFalse()
        assertThat(BatteryGuard.canResume(16, charging = true)).isTrue()
        assertThat(BatteryGuard.canResume(14, charging = true)).isFalse()
    }

    @Test
    fun thermal_downgrades_at_moderate_and_resumes_at_light() {
        assertThat(ThermalGuard.shouldDowngrade(1)).isFalse()
        assertThat(ThermalGuard.shouldDowngrade(2)).isTrue()
        assertThat(ThermalGuard.shouldDowngrade(6)).isTrue()
        assertThat(ThermalGuard.canResume(1)).isTrue()
        assertThat(ThermalGuard.canResume(2)).isFalse()
    }

    @Test
    fun silence_tracker_pauses_after_fifteen_minutes_of_stillness() {
        var now = 0L
        val tracker = SilenceTracker(clock = { now })

        assertThat(tracker.shouldPause(stationary = true)).isFalse()

        now = 15 * 60 * 1000L
        assertThat(tracker.shouldPause(stationary = true)).isTrue()
        assertThat(tracker.shouldPause(stationary = false)).isFalse()
    }

    @Test
    fun speech_or_motion_resets_the_silence_clock() {
        var now = 0L
        val tracker = SilenceTracker(clock = { now })

        now = 14 * 60 * 1000L
        tracker.onSpeech()
        now = 20 * 60 * 1000L
        assertThat(tracker.shouldPause(stationary = true)).isFalse()

        tracker.onMotion()
        now = 30 * 60 * 1000L
        assertThat(tracker.shouldPause(stationary = true)).isFalse()

        now = 46 * 60 * 1000L
        assertThat(tracker.shouldPause(stationary = true)).isTrue()
    }
}
