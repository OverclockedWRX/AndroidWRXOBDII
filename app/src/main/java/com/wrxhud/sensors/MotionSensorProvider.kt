package com.wrxhud.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MotionReading(
    val pitchDeg: Float? = null,
    val rollDeg: Float? = null,
    val yawDeg: Float? = null,
    /**
     * Dynamic acceleration magnitude in g with gravity removed (0 ≈ parked).
     * This is how many extra Gs the car is pulling, not total including 1g gravity.
     */
    val gForce: Float? = null,
    val available: Boolean = false
)

/**
 * Pitch / roll remapped to the current screen orientation (landscape dash mount),
 * plus dynamic G from [Sensor.TYPE_LINEAR_ACCELERATION] (gravity stripped).
 */
class MotionSensorProvider(context: Context) : SensorEventListener {
    companion object {
        private const val TAG = "MotionSensors"
    }

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val linearAccel: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _reading = MutableStateFlow(MotionReading())
    val reading: StateFlow<MotionReading> = _reading.asStateFlow()

    private val deviceRotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var latestG: Float? = null
    private var latestPitch: Float? = null
    private var latestRoll: Float? = null
    private var latestYaw: Float? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val rate = SensorManager.SENSOR_DELAY_UI
        rotationSensor?.let { sensorManager.registerListener(this, it, rate) }
        // Prefer linear acceleration (gravity removed) for the G meter.
        val gSensor = linearAccel ?: accelerometer
        gSensor?.let { sensorManager.registerListener(this, it, rate) }
        _reading.value = _reading.value.copy(
            available = rotationSensor != null || gSensor != null
        )
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    latestG = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Fallback only when linear acceleration is unavailable: strip ~1g baseline.
                    if (linearAccel == null) {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        val totalG = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
                        latestG = (totalG - 1f).coerceAtLeast(0f)
                    }
                }
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(deviceRotationMatrix, event.values)
                    val world = if (remapToScreenAxes(deviceRotationMatrix, remappedRotationMatrix)) {
                        remappedRotationMatrix
                    } else {
                        deviceRotationMatrix
                    }
                    SensorManager.getOrientation(world, orientation)
                    // azimuth, pitch, roll — relative to the current screen orientation
                    latestYaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    latestPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    latestRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                }
            }
            publish()
        } catch (t: Throwable) {
            Log.w(TAG, "Sensor update failed", t)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Remap device axes so +Y is the top of the *current* screen and +X is right.
     * Critical for landscape dash mounts — without this, pitch/roll track the phone's
     * natural portrait axes and the WRX graphics disagree with the car.
     */
    private fun remapToScreenAxes(inR: FloatArray, outR: FloatArray): Boolean {
        val rotation = currentDisplayRotation()
        val (xAxis, yAxis) = when (rotation) {
            Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> return false
        }
        return SensorManager.remapCoordinateSystem(inR, xAxis, yAxis, outR)
    }

    /**
     * Never use [Context.getDisplay] on an Application context — on API 30+ it can throw
     * UnsupportedOperationException ("Context not associated with a display"), which crashed
     * the mount-calibration screen as soon as rotation sensors fired.
     */
    private fun currentDisplayRotation(): Int {
        return runCatching {
            displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }.getOrNull() ?: Surface.ROTATION_0
    }

    private fun publish() {
        _reading.value = MotionReading(
            pitchDeg = latestPitch,
            rollDeg = latestRoll,
            yawDeg = latestYaw,
            gForce = latestG,
            available = rotationSensor != null || linearAccel != null || accelerometer != null
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
