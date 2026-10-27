package io.github.markyu0401.everheard.capture

/**
 * Pure transition function. Precedence: explicit stop/error > battery > schedule
 * > thermal > microphone availability > excluded-app playback > silence resume.
 * A user pause is not overridden by a schedule tick; the service expires it with
 * its own timer.
 */
object CaptureStateMachine {

    fun transition(
        current: CaptureState,
        event: CaptureEvent,
        context: CaptureContext
    ): CaptureState = when (event) {
        CaptureEvent.UserStop, CaptureEvent.Boot -> CaptureState.STOPPED
        CaptureEvent.ServiceKilled -> CaptureState.ERROR
        is CaptureEvent.Failure -> CaptureState.ERROR

        CaptureEvent.UserStart -> resumeState(context)

        is CaptureEvent.UserPause ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_USER
            }

        CaptureEvent.ScheduleBlock ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_SCHEDULE
            }

        CaptureEvent.ScheduleAllow -> when (current) {
            CaptureState.ARMED, CaptureState.PAUSED_SCHEDULE -> resumeState(context)
            else -> current
        }

        CaptureEvent.BatteryLow ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_BATTERY
            }

        CaptureEvent.BatteryRecovered -> when (current) {
            CaptureState.PAUSED_BATTERY -> resumeState(context)
            else -> current
        }

        CaptureEvent.ThermalHigh ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_THERMAL
            }

        CaptureEvent.ThermalNormal -> when (current) {
            CaptureState.PAUSED_THERMAL -> resumeState(context)
            else -> current
        }

        CaptureEvent.SilenceTimeout ->
            if (current == CaptureState.RECORDING) CaptureState.PAUSED_SILENCE else current

        CaptureEvent.MotionDetected, CaptureEvent.ScreenUnlocked ->
            if (current == CaptureState.PAUSED_SILENCE) {
                if (context.scheduleAllows && context.batteryOk) {
                    resumeState(context)
                } else {
                    current
                }
            } else {
                current
            }

        CaptureEvent.MicBusy -> when (current) {
            CaptureState.STOPPED,
            CaptureState.ERROR,
            CaptureState.PAUSED_USER,
            CaptureState.PAUSED_SCHEDULE,
            CaptureState.PAUSED_BATTERY,
            CaptureState.PAUSED_THERMAL -> current
            else -> CaptureState.PAUSED_MIC_BUSY
        }

        CaptureEvent.MicAvailable ->
            if (current == CaptureState.PAUSED_MIC_BUSY) resumeState(context) else current

        CaptureEvent.ExcludedAppPlaying -> when (current) {
            CaptureState.STOPPED,
            CaptureState.ERROR,
            CaptureState.PAUSED_USER,
            CaptureState.PAUSED_SCHEDULE,
            CaptureState.PAUSED_BATTERY,
            CaptureState.PAUSED_THERMAL -> current
            CaptureState.PAUSED_MIC_BUSY -> current
            else -> if (context.micBusy) {
                CaptureState.PAUSED_MIC_BUSY
            } else {
                CaptureState.PAUSED_APP_AUDIO
            }
        }

        CaptureEvent.ExcludedAppStopped ->
            if (current == CaptureState.PAUSED_APP_AUDIO) resumeState(context) else current
    }

    private fun resumeState(context: CaptureContext): CaptureState = when {
        !context.batteryOk -> CaptureState.PAUSED_BATTERY
        !context.scheduleAllows -> CaptureState.PAUSED_SCHEDULE
        context.micBusy -> CaptureState.PAUSED_MIC_BUSY
        context.excludedAppPlaying -> CaptureState.PAUSED_APP_AUDIO
        else -> CaptureState.RECORDING
    }
}
