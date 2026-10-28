package io.github.markyu0401.everheard.capture

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.capture.guards.MicContention
import org.junit.Test

class CaptureEvaluatorTest {

    private val now = 1_000_000L

    private fun inputs(
        batteryLevel: Int = 80,
        charging: Boolean = false,
        thermalStatus: Int = 0,
        scheduleAllows: Boolean = true,
        micContention: MicContention = MicContention(),
        excludedAppPlaying: Boolean = false,
        silenceExpired: Boolean = false,
        motionDetected: Boolean = false,
        screenUnlocked: Boolean = false,
        pauseUntilMillis: Long = 0L
    ) = CaptureInputs(
        nowMillis = now,
        pauseUntilMillis = pauseUntilMillis,
        batteryLevel = batteryLevel,
        charging = charging,
        thermalStatus = thermalStatus,
        scheduleAllows = scheduleAllows,
        micContention = micContention,
        excludedAppPlaying = excludedAppPlaying,
        silenceExpired = silenceExpired,
        motionDetected = motionDetected,
        screenUnlocked = screenUnlocked
    )

    @Test
    fun recording_stops_when_our_own_capture_is_silenced() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(micContention = MicContention(ownSilenced = true))
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_MIC_BUSY)
        assertThat(decision.micInterrupted).isTrue()
    }

    @Test
    fun armed_waiting_for_a_busy_mic_is_not_an_interruption() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.ARMED,
            inputs(micContention = MicContention(otherActive = true))
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_MIC_BUSY)
        assertThat(decision.micInterrupted).isFalse()
    }

    @Test
    fun mic_becoming_free_resumes_recording() {
        val decision = CaptureEvaluator.evaluate(CaptureState.PAUSED_MIC_BUSY, inputs())
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun battery_pause_holds_at_sixteen_percent() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_BATTERY,
            inputs(batteryLevel = 16)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_BATTERY)
    }

    @Test
    fun battery_pause_resumes_at_thirty_percent() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_BATTERY,
            inputs(batteryLevel = 30)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun battery_pause_resumes_while_charging_at_sixteen_percent() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_BATTERY,
            inputs(batteryLevel = 16, charging = true)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun recording_stops_below_fifteen_percent() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(batteryLevel = 14)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_BATTERY)
    }

    @Test
    fun silence_pause_resumes_on_screen_unlock() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_SILENCE,
            inputs(screenUnlocked = true)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun silence_pause_resumes_on_motion() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_SILENCE,
            inputs(motionDetected = true)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun silence_resume_still_respects_the_schedule() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_SILENCE,
            inputs(motionDetected = true, scheduleAllows = false)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_SCHEDULE)
    }

    @Test
    fun excluded_app_playback_pauses_recording() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(excludedAppPlaying = true)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_APP_AUDIO)
    }

    @Test
    fun mic_busy_wins_over_excluded_app_audio() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(
                excludedAppPlaying = true,
                micContention = MicContention(ownSilenced = true)
            )
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_MIC_BUSY)
        assertThat(decision.micInterrupted).isTrue()
    }

    @Test
    fun user_pause_is_not_overridden_by_a_schedule_allow() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(pauseUntilMillis = now + 60_000L)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_USER)
    }

    @Test
    fun an_expired_user_pause_resumes() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_USER,
            inputs(pauseUntilMillis = now - 1_000L)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun thermal_pause_resumes_at_light() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_THERMAL,
            inputs(thermalStatus = 1)
        )
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun thermal_high_pauses_recording() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(thermalStatus = 2)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_THERMAL)
    }

    @Test
    fun mic_available_with_low_battery_goes_to_battery_pause() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.PAUSED_MIC_BUSY,
            inputs(batteryLevel = 10)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_BATTERY)
    }

    @Test
    fun silence_timeout_pauses_a_recording() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.RECORDING,
            inputs(silenceExpired = true)
        )
        assertThat(decision.state).isEqualTo(CaptureState.PAUSED_SILENCE)
    }

    @Test
    fun stopped_stays_stopped() {
        val decision = CaptureEvaluator.evaluate(
            CaptureState.STOPPED,
            inputs(micContention = MicContention(ownSilenced = true))
        )
        assertThat(decision.state).isEqualTo(CaptureState.STOPPED)
    }

    @Test
    fun error_stays_error() {
        val decision = CaptureEvaluator.evaluate(CaptureState.ERROR, inputs())
        assertThat(decision.state).isEqualTo(CaptureState.ERROR)
    }

    @Test
    fun all_clear_recording_stays_recording() {
        val decision = CaptureEvaluator.evaluate(CaptureState.RECORDING, inputs())
        assertThat(decision.state).isEqualTo(CaptureState.RECORDING)
        assertThat(decision.micInterrupted).isFalse()
    }
}
