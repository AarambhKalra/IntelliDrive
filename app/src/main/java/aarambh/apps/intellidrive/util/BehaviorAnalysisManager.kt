package aarambh.apps.intellidrive.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class BehaviorAnalysisManager(
    private val sensorManager: SensorManager,
    private val onHarshBrakingDetected: (Float) -> Unit,
    private val onOverspeedDetected: (Float) -> Unit
) : SensorEventListener {

    private var accelerometer: Sensor? = null
    private var lastAcceleration = 0f
    private val BRAKING_THRESHOLD = 4.9f // m/s^2

    // Low-pass filter variables
    private val ALPHA = 0.8f
    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)

    private var isAnalyzing = false
    private val SPEED_LIMIT_KMH = 60f

    fun start() {
        if (isAnalyzing) return
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isAnalyzing = true
        }
    }

    fun stop() {
        if (!isAnalyzing) return
        sensorManager.unregisterListener(this)
        isAnalyzing = false
    }

    fun analyzeSpeed(speedMetersPerSec: Float) {
        val speedKmh = speedMetersPerSec * 3.6f
        if (speedKmh > SPEED_LIMIT_KMH) {
            onOverspeedDetected(speedKmh)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // Apply low-pass filter to isolate gravity
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0]
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1]
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2]

            // Remove gravity to get linear acceleration
            linearAcceleration[0] = event.values[0] - gravity[0]
            linearAcceleration[1] = event.values[1] - gravity[1]
            linearAcceleration[2] = event.values[2] - gravity[2]

            // Calculate magnitude of acceleration (primarily looking at Y axis for braking in most device orientations,
            // but magnitude is safer for arbitrary mounting)
            val magnitude = sqrt(
                (linearAcceleration[0] * linearAcceleration[0] +
                 linearAcceleration[1] * linearAcceleration[1] +
                 linearAcceleration[2] * linearAcceleration[2]).toDouble()
            ).toFloat()

            // Detect sudden deceleration (braking)
            // A simple implementation: if magnitude exceeds threshold.
            // In a real app, you'd check the direction of the vector to ensure it's braking (forward deceleration)
            if (magnitude > BRAKING_THRESHOLD) {
                // To prevent multiple triggers for one event, you'd add a cooldown.
                onHarshBrakingDetected(magnitude)
            }
            
            lastAcceleration = magnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
