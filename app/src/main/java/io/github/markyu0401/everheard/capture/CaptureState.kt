package io.github.markyu0401.everheard.capture

/**
 * The capture lifecycle. The service is "alive" in every state except STOPPED
 * and ERROR; the microphone is open only in RECORDING.
 */
enum class CaptureState {
    STOPPED,
    ARMED,
    RECORDING,
    PAUSED_SCHEDULE,
    PAUSED_BATTERY,
    PAUSED_THERMAL,
    PAUSED_SILENCE,
    PAUSED_USER,
    ERROR;

    val isServiceAlive: Boolean
        get() = this != STOPPED && this != ERROR

    val isMicrophoneOpen: Boolean
        get() = this == RECORDING
}

sealed interface CaptureEvent {
    data object UserStart : CaptureEvent
    data object UserStop : CaptureEvent
    data class UserPause(val minutes: Int) : CaptureEvent
    data object ScheduleAllow : CaptureEvent
    data object ScheduleBlock : CaptureEvent
    data object BatteryLow : CaptureEvent
    data object BatteryRecovered : CaptureEvent
    data object ThermalHigh : CaptureEvent
    data object ThermalNormal : CaptureEvent
    data object SilenceTimeout : CaptureEvent
    data object MotionDetected : CaptureEvent
    data object ScreenUnlocked : CaptureEvent
    data object Boot : CaptureEvent
    data object ServiceKilled : CaptureEvent
    data class Failure(val message: String) : CaptureEvent
}

data class CaptureContext(
    val scheduleAllows: Boolean = true,
    val batteryOk: Boolean = true
)
