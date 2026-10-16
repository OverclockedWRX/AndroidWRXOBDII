package com.wrxhud.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MotionReading(
    val pitchDeg: Float? = null,
    val rollDeg: Float? = null,
    val yawDeg: Float? = null,
    /** Total acceleration magnitude in g (1.0 ≈ parked / level). */
    val gForce: Float? = null,
    val available: Boolean = false
)

/**
 * Pitch / roll / yaw from rotation vector (or game rotation) plus g-force from accelerometer.
 */
class MotionSensorProvider(context: Context) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _reading = MutableStateFlow(MotionReading())
    val reading: StateFlow<MotionReading> = _reading.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var latestG: Float? = null
    private var latestPitch: Float? = null
    private var latestRoll: Float? = null
    private var latestYaw: Float? = null

    fun start() {
        val rate = SensorManager.SENSOR_DELAY_UI
        rotationSensor?.let { sensorManager.registerListener(this, it, rate) }
        accelerometer?.let { sensorManager.registerListener(this, it, rate) }
        _reading.value = _reading.value.copy(
            available = rotationSensor != null || accelerometer != null
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                latestG = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // azimuth (yaw), pitch, roll — radians → degrees
                latestYaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
                latestPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                latestRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }
        publish()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish() {
        _reading.value = MotionReading(
            pitchDeg = latestPitch,
            rollDeg = latestRoll,
            yawDeg = latestYaw,
            gForce = latestG,
            available = rotationSensor != null || accelerometer != null
        )
    }
}

fun formatSignedDegrees(deg: Float?): String {
    if (deg == null) return "—"
    val v = deg.roundToInt()
    return if (v > 0) "+$v°" else "$v°"
}

fun formatGForce(g: Float?): String {
    if (g == null) return "—"
    return "%.2fg".format(g)
}
