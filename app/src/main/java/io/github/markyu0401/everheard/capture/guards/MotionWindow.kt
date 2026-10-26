package io.github.markyu0401.everheard.capture.guards

/**
 * Pure time window: "no significant motion for [windowMs]". Kept separate from
 * the sensor listener so it can be tested without an Android device.
 */
class MotionWindow(private val windowMs: Long) {

    private var lastMotionAt: Long = Long.MIN_VALUE

    fun reset(now: Long) {
        lastMotionAt = now
    }

    fun onMotion(now: Long) {
        lastMotionAt = now
    }

    fun isStationary(now: Long): Boolean =
        lastMotionAt != Long.MIN_VALUE && now - lastMotionAt >= windowMs
}
