package io.github.markyu0401.everheard.capture

/**
 * Pure transition function. Precedence: explicit stop/error > battery > schedule
 * block > schedule allow > silence resume. A user pause is not overridden by a
 * schedule tick; the service expires it with its own timer.
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

        CaptureEvent.UserStart -> when {
            !context.batteryOk -> CaptureState.PAUSED_BATTERY
            !context.scheduleAllows -> CaptureState.PAUSED_SCHEDULE
            else -> CaptureState.RECORDING
        }

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
            CaptureState.ARMED, CaptureState.PAUSED_SCHEDULE -> when {
                !context.batteryOk -> CaptureState.PAUSED_BATTERY
                !context.scheduleAllows -> CaptureState.PAUSED_SCHEDULE
                else -> CaptureState.RECORDING
            }
            else -> current
        }

        CaptureEvent.BatteryLow ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_BATTERY
            }

        CaptureEvent.BatteryRecovered -> when (current) {
            CaptureState.PAUSED_BATTERY -> when {
                !context.scheduleAllows -> CaptureState.PAUSED_SCHEDULE
                !context.batteryOk -> CaptureState.PAUSED_BATTERY
                else -> CaptureState.RECORDING
            }
            else -> current
        }

        CaptureEvent.ThermalHigh ->
            if (current == CaptureState.STOPPED || current == CaptureState.ERROR) {
                current
            } else {
                CaptureState.PAUSED_THERMAL
            }

        CaptureEvent.ThermalNormal -> when (current) {
            CaptureState.PAUSED_THERMAL -> when {
                !context.batteryOk -> CaptureState.PAUSED_BATTERY
                !context.scheduleAllows -> CaptureState.PAUSED_SCHEDULE
                else -> CaptureState.RECORDING
            }
            else -> current
        }

        CaptureEvent.SilenceTimeout ->
            if (current == CaptureState.RECORDING) CaptureState.PAUSED_SILENCE else current

        CaptureEvent.MotionDetected, CaptureEvent.ScreenUnlocked ->
            if (current == CaptureState.PAUSED_SILENCE &&
                context.scheduleAllows &&
                context.batteryOk
            ) {
                CaptureState.RECORDING
            } else {
                current
            }
    }
}
