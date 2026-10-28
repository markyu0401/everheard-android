package io.github.markyu0401.everheard.capture

import io.github.markyu0401.everheard.capture.guards.BatteryGuard
import io.github.markyu0401.everheard.capture.guards.MicContention
import io.github.markyu0401.everheard.capture.guards.ThermalGuard

/**
 * Everything the guard chain needs for one evaluation. Kept as plain data so
 * the whole precedence order can be unit-tested without an Android device.
 */
data class CaptureInputs(
    val nowMillis: Long,
    val pauseUntilMillis: Long = 0L,
    val batteryLevel: Int = 100,
    val charging: Boolean = false,
    val batteryGuardEnabled: Boolean = true,
    val thermalStatus: Int = 0,
    val scheduleAllows: Boolean = true,
    val micContention: MicContention = MicContention(),
    val excludedAppPlaying: Boolean = false,
    val silenceExpired: Boolean = false,
    val motionDetected: Boolean = false,
    val screenUnlocked: Boolean = false
)

data class CaptureDecision(
    val state: CaptureState,
    /**
     * True only when a recording was actually interrupted by another app taking
     * the microphone. Merely waiting for a busy mic while armed is not an
     * interruption and must not raise an alert.
     */
    val micInterrupted: Boolean = false
)

/**
 * Pure guard evaluation. Precedence matches the plan: explicit stop/error >
 * user pause > battery > schedule > thermal > microphone availability >
 * excluded-app playback > silence.
 *
 * Every comparison uses the state produced by the previous step (`next`), not
 * the state at entry; mixing the two made a multi-guard transition depend on
 * evaluation order in the old service loop.
 */
object CaptureEvaluator {

    fun evaluate(current: CaptureState, inputs: CaptureInputs): CaptureDecision {
        if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
            return CaptureDecision(current)
        }

        // Hysteresis: a battery pause resumes at 30 % (or while charging), while
        // recording only stops below 15 %. The whole guard is optional in
        // Settings; disabling it makes the battery level irrelevant.
        val batteryOk = when {
            !inputs.batteryGuardEnabled -> true
            current == CaptureState.PAUSED_BATTERY ->
                BatteryGuard.canResume(inputs.batteryLevel, inputs.charging)
            else -> BatteryGuard.isBatteryOk(inputs.batteryLevel)
        }
        val context = CaptureContext(
            scheduleAllows = inputs.scheduleAllows,
            batteryOk = batteryOk,
            micBusy = inputs.micContention.busy,
            excludedAppPlaying = inputs.excludedAppPlaying
        )

        var next = current

        val pauseActive = inputs.pauseUntilMillis > inputs.nowMillis
        if (pauseActive && next != CaptureState.PAUSED_USER) {
            next = CaptureStateMachine.transition(
                next,
                CaptureEvent.UserPause(minutes = 0),
                context
            )
        }
        if (next == CaptureState.PAUSED_USER && !pauseActive) {
            next = CaptureStateMachine.transition(next, CaptureEvent.UserStart, context)
        }

        if (!batteryOk) {
            next = CaptureStateMachine.transition(next, CaptureEvent.BatteryLow, context)
        } else if (next == CaptureState.PAUSED_BATTERY) {
            next = CaptureStateMachine.transition(next, CaptureEvent.BatteryRecovered, context)
        }

        if (!inputs.scheduleAllows) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ScheduleBlock, context)
        } else if (next == CaptureState.ARMED || next == CaptureState.PAUSED_SCHEDULE) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ScheduleAllow, context)
        }

        if (ThermalGuard.shouldDowngrade(inputs.thermalStatus)) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ThermalHigh, context)
        } else if (next == CaptureState.PAUSED_THERMAL) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ThermalNormal, context)
        }

        if (inputs.micContention.busy) {
            next = CaptureStateMachine.transition(next, CaptureEvent.MicBusy, context)
        } else if (next == CaptureState.PAUSED_MIC_BUSY) {
            next = CaptureStateMachine.transition(next, CaptureEvent.MicAvailable, context)
        }

        if (inputs.excludedAppPlaying) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ExcludedAppPlaying, context)
        } else if (next == CaptureState.PAUSED_APP_AUDIO) {
            next = CaptureStateMachine.transition(next, CaptureEvent.ExcludedAppStopped, context)
        }

        // A silence pause is a low-power state; user motion or an unlock is the
        // only way out. Without these events the old service stayed paused
        // forever after 15 quiet minutes.
        if (next == CaptureState.PAUSED_SILENCE &&
            (inputs.motionDetected || inputs.screenUnlocked)
        ) {
            next = CaptureStateMachine.transition(
                next,
                if (inputs.screenUnlocked) {
                    CaptureEvent.ScreenUnlocked
                } else {
                    CaptureEvent.MotionDetected
                },
                context
            )
        }

        if (next == CaptureState.RECORDING && inputs.silenceExpired) {
            next = CaptureStateMachine.transition(next, CaptureEvent.SilenceTimeout, context)
        }

        return CaptureDecision(
            state = next,
            micInterrupted = current == CaptureState.RECORDING &&
                next == CaptureState.PAUSED_MIC_BUSY
        )
    }
}
