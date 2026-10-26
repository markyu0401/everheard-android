package io.github.markyu0401.everheard.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureStateMachineTest {

    private val ok = CaptureContext(scheduleAllows = true, batteryOk = true)
    private val blocked = CaptureContext(scheduleAllows = false, batteryOk = true)
    private val lowBattery = CaptureContext(scheduleAllows = true, batteryOk = false)

    @Test
    fun user_start_records_when_schedule_allows_and_battery_is_ok() {
        val next = CaptureStateMachine.transition(
            CaptureState.ARMED,
            CaptureEvent.UserStart,
            ok
        )
        assertThat(next).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun user_start_respects_a_schedule_block() {
        val next = CaptureStateMachine.transition(
            CaptureState.STOPPED,
            CaptureEvent.UserStart,
            blocked
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_SCHEDULE)
    }

    @Test
    fun user_start_respects_low_battery() {
        val next = CaptureStateMachine.transition(
            CaptureState.ARMED,
            CaptureEvent.UserStart,
            lowBattery
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_BATTERY)
    }

    @Test
    fun schedule_block_wins_over_recording() {
        val next = CaptureStateMachine.transition(
            CaptureState.RECORDING,
            CaptureEvent.ScheduleBlock,
            blocked
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_SCHEDULE)
    }

    @Test
    fun schedule_allow_resumes_from_a_schedule_pause() {
        val next = CaptureStateMachine.transition(
            CaptureState.PAUSED_SCHEDULE,
            CaptureEvent.ScheduleAllow,
            ok
        )
        assertThat(next).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun schedule_tick_does_not_override_a_user_pause() {
        val next = CaptureStateMachine.transition(
            CaptureState.PAUSED_USER,
            CaptureEvent.ScheduleAllow,
            ok
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_USER)
    }

    @Test
    fun battery_low_pauses_even_while_recording() {
        val next = CaptureStateMachine.transition(
            CaptureState.RECORDING,
            CaptureEvent.BatteryLow,
            lowBattery
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_BATTERY)
    }

    @Test
    fun battery_recovery_respects_the_schedule() {
        val next = CaptureStateMachine.transition(
            CaptureState.PAUSED_BATTERY,
            CaptureEvent.BatteryRecovered,
            blocked
        )
        assertThat(next).isEqualTo(CaptureState.PAUSED_SCHEDULE)
    }

    @Test
    fun silence_pauses_only_from_recording() {
        assertThat(
            CaptureStateMachine.transition(
                CaptureState.RECORDING,
                CaptureEvent.SilenceTimeout,
                ok
            )
        ).isEqualTo(CaptureState.PAUSED_SILENCE)

        assertThat(
            CaptureStateMachine.transition(
                CaptureState.ARMED,
                CaptureEvent.SilenceTimeout,
                ok
            )
        ).isEqualTo(CaptureState.ARMED)
    }

    @Test
    fun motion_resumes_from_a_silence_pause_only_when_allowed() {
        assertThat(
            CaptureStateMachine.transition(
                CaptureState.PAUSED_SILENCE,
                CaptureEvent.MotionDetected,
                ok
            )
        ).isEqualTo(CaptureState.RECORDING)

        assertThat(
            CaptureStateMachine.transition(
                CaptureState.PAUSED_SILENCE,
                CaptureEvent.MotionDetected,
                blocked
            )
        ).isEqualTo(CaptureState.PAUSED_SILENCE)
    }

    @Test
    fun thermal_high_pauses_and_normal_resumes() {
        assertThat(
            CaptureStateMachine.transition(
                CaptureState.RECORDING,
                CaptureEvent.ThermalHigh,
                ok
            )
        ).isEqualTo(CaptureState.PAUSED_THERMAL)

        assertThat(
            CaptureStateMachine.transition(
                CaptureState.PAUSED_THERMAL,
                CaptureEvent.ThermalNormal,
                ok
            )
        ).isEqualTo(CaptureState.RECORDING)
    }

    @Test
    fun thermal_normal_respects_the_schedule() {
        assertThat(
            CaptureStateMachine.transition(
                CaptureState.PAUSED_THERMAL,
                CaptureEvent.ThermalNormal,
                blocked
            )
        ).isEqualTo(CaptureState.PAUSED_SCHEDULE)
    }

    @Test
    fun stop_and_boot_leave_the_service_stopped() {
        assertThat(
            CaptureStateMachine.transition(CaptureState.RECORDING, CaptureEvent.UserStop, ok)
        ).isEqualTo(CaptureState.STOPPED)
        assertThat(
            CaptureStateMachine.transition(CaptureState.ARMED, CaptureEvent.Boot, ok)
        ).isEqualTo(CaptureState.STOPPED)
    }

    @Test
    fun failure_enters_error() {
        assertThat(
            CaptureStateMachine.transition(
                CaptureState.RECORDING,
                CaptureEvent.Failure("microphone unavailable"),
                ok
            )
        ).isEqualTo(CaptureState.ERROR)
    }

    @Test
    fun microphone_is_open_only_while_recording() {
        assertThat(CaptureState.RECORDING.isMicrophoneOpen).isTrue()
        assertThat(CaptureState.ARMED.isMicrophoneOpen).isFalse()
        assertThat(CaptureState.PAUSED_SCHEDULE.isMicrophoneOpen).isFalse()
        assertThat(CaptureState.PAUSED_SILENCE.isMicrophoneOpen).isFalse()
    }

    @Test
    fun service_is_alive_in_every_state_except_stopped_and_error() {
        assertThat(CaptureState.ARMED.isServiceAlive).isTrue()
        assertThat(CaptureState.RECORDING.isServiceAlive).isTrue()
        assertThat(CaptureState.PAUSED_BATTERY.isServiceAlive).isTrue()
        assertThat(CaptureState.STOPPED.isServiceAlive).isFalse()
        assertThat(CaptureState.ERROR.isServiceAlive).isFalse()
    }
}
