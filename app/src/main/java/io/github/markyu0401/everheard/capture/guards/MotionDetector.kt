package io.github.markyu0401.everheard.capture.guards

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Accelerometer-based "is the phone moving?" signal. Falls back to the caller's
 * screen-state proxy when no accelerometer is present. No runtime permission is
 * needed for a raw accelerometer.
 */
class MotionDetector(
    context: Context,
    private val windowMs: Long = 5 * 60_000L
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val window = MotionWindow(windowMs)

    private var lastMagnitude = 0f
    private var registered = false

    fun start() {
        if (accelerometer == null || registered) return
        window.reset(SystemClock.elapsedRealtime())
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    fun hasSensor(): Boolean = accelerometer != null

    fun isStationary(): Boolean {
        if (accelerometer == null) return false
        return window.isStationary(SystemClock.elapsedRealtime())
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        if (lastMagnitude > 0f && abs(magnitude - lastMagnitude) > 0.8f) {
            window.onMotion(SystemClock.elapsedRealtime())
        }
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
