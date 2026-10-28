package io.github.markyu0401.everheard.capture.guards

/**
 * Battery policy with hysteresis: close the microphone below 15 %, and do not
 * resume a battery pause until 30 % (or while charging and at least 15 %).
 * Without hysteresis a phone hovering at 15–16 % flaps between RECORDING and
 * PAUSED_BATTERY on every tick.
 */
object BatteryGuard {
    const val LOW_PERCENT = 15
    const val RESUME_PERCENT = 30

    fun isBatteryOk(levelPercent: Int): Boolean = levelPercent >= LOW_PERCENT

    fun shouldPause(levelPercent: Int): Boolean = levelPercent < LOW_PERCENT

    fun canResume(levelPercent: Int, charging: Boolean = false): Boolean =
        levelPercent >= RESUME_PERCENT || (charging && levelPercent >= LOW_PERCENT)
}

/**
 * Thermal policy: close the microphone at MODERATE or above, resume at LIGHT or
 * below. The service stays alive while downgraded so the notification does not
 * disappear.
 */
object ThermalGuard {
    const val LIGHT = 1
    const val MODERATE = 2

    fun shouldDowngrade(status: Int): Boolean = status >= MODERATE

    fun canResume(status: Int): Boolean = status <= LIGHT
}

/**
 * Silence policy: pause after 15 minutes without speech while the device is
 * stationary. Any speech or motion resets the clock.
 */
class SilenceTracker(
    private val timeoutMs: Long = 15 * 60 * 1000L,
    private val clock: () -> Long
) {
    private var lastActivityAt: Long = clock()

    fun onSpeech() {
        lastActivityAt = clock()
    }

    fun onMotion() {
        lastActivityAt = clock()
    }

    fun shouldPause(stationary: Boolean): Boolean =
        stationary && (clock() - lastActivityAt) >= timeoutMs

    fun reset() {
        lastActivityAt = clock()
    }

    fun idleMs(): Long = clock() - lastActivityAt
}
