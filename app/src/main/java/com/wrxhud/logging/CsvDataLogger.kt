package com.wrxhud.logging

import android.content.Context
import android.util.Log
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Appends HUD telemetry rows to a CSV under app-specific external storage
 * (`Android/data/.../files/datalogs/`). No runtime storage permission required.
 */
class CsvDataLogger(context: Context) {
    companion object {
        private const val TAG = "CsvDataLogger"
        private val HEADER = listOf(
            "timestamp_ms",
            "iso_utc",
            "rpm",
            "gps_speed_mps",
            "gps_speed_mph",
            "gps_speed_kph",
            "obd_speed_kph",
            "gear",
            "coolant_c",
            "oil_temp_c",
            "iat_c",
            "boost_psi",
            "afr",
            "oil_pressure_psi",
            "battery_v",
            "fuel_pct",
            "throttle_pct",
            "accel_pedal_pct",
            "brake_pct",
            "mil",
            "pitch_deg",
            "roll_deg",
            "yaw_deg",
            "g_force",
            "lateral_g",
            "longitudinal_g",
            "speed_limit",
            "speed_limit_unit",
            "gps_fix",
            "obd_connected",
            "simulator",
            "alarm_cel",
            "alarm_low_fuel",
            "alarm_high_coolant",
            "alarm_high_oil_temp",
            "alarm_high_iat",
            "alarm_overboost",
            "alarm_low_battery",
            "alarm_low_oil_pressure",
            "alarm_link_lost",
            "alarm_gps_lost",
            "alarm_knock",
            "alarm_dam"
        ).joinToString(",")
    }

    private val appContext = context.applicationContext
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _activeFileName = MutableStateFlow<String?>(null)
    val activeFileName: StateFlow<String?> = _activeFileName.asStateFlow()

    private var writer: BufferedWriter? = null
    private var file: File? = null

    fun logDir(): File {
        val dir = File(appContext.getExternalFilesDir(null), "datalogs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Synchronized
    fun start(): File? {
        if (_recording.value) return file
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = File(logDir(), "wrx_log_$stamp.csv")
            val w = BufferedWriter(FileWriter(out, false))
            w.write(HEADER)
            w.newLine()
            w.flush()
            writer = w
            file = out
            _activeFileName.value = out.name
            _recording.value = true
            Log.i(TAG, "Logging started: ${out.absolutePath}")
            out
        }.onFailure { t ->
            Log.e(TAG, "Failed to start log", t)
            stopInternal()
        }.getOrNull()
    }

    @Synchronized
    fun stop(): File? {
        val closed = file
        stopInternal()
        if (closed != null) Log.i(TAG, "Logging stopped: ${closed.absolutePath}")
        return closed
    }

    private fun stopInternal() {
        runCatching { writer?.flush(); writer?.close() }
        writer = null
        file = null
        _recording.value = false
        _activeFileName.value = null
    }

    @Synchronized
    fun append(
        snapshot: VehicleSnapshot,
        motion: MotionReading,
        speedLimit: SpeedLimitReading
    ) {
        val w = writer ?: return
        val now = System.currentTimeMillis()
        val mps = snapshot.gpsSpeedMps
        val row = listOf(
            now.toString(),
            isoFormat.format(Date(now)),
            fmt(snapshot.rpm),
            fmt(mps),
            fmt(mps?.let { UnitConverters.mpsToMph(it) }),
            fmt(mps?.let { UnitConverters.mpsToKph(it) }),
            fmt(snapshot.obdSpeedKph),
            snapshot.gear?.toString().orEmpty(),
            fmt(snapshot.coolantC),
            fmt(snapshot.oilTempC),
            fmt(snapshot.intakeAirTempC),
            fmt(snapshot.boostPsi),
            fmt(snapshot.afr),
            fmt(snapshot.oilPressurePsi),
            fmt(snapshot.batteryVolts),
            fmt(snapshot.fuelLevelPercent),
            fmt(snapshot.throttlePercent),
            fmt(snapshot.acceleratorPercent),
            fmt(snapshot.brakePercent),
            snapshot.milOn?.let { if (it) "1" else "0" }.orEmpty(),
            fmt(motion.pitchDeg),
            fmt(motion.rollDeg),
            fmt(motion.yawDeg),
            fmt(motion.gForce),
            fmt(motion.lateralG),
            fmt(motion.longitudinalG),
            speedLimit.limit?.toString().orEmpty(),
            speedLimit.unit.name,
            if (snapshot.gpsHasFix) "1" else "0",
            if (snapshot.obdConnected) "1" else "0",
            if (snapshot.usingSimulator) "1" else "0",
            bool(snapshot.alarms.cel),
            bool(snapshot.alarms.lowFuel),
            bool(snapshot.alarms.highCoolant),
            bool(snapshot.alarms.highOilTemp),
            bool(snapshot.alarms.highIat),
            bool(snapshot.alarms.overboost),
            bool(snapshot.alarms.lowBattery),
            bool(snapshot.alarms.lowOilPressure),
            bool(snapshot.alarms.linkLost),
            bool(snapshot.alarms.gpsLost),
            bool(snapshot.alarms.knock),
            bool(snapshot.alarms.dam)
        ).joinToString(",")
        runCatching {
            w.write(row)
            w.newLine()
        }.onFailure { t ->
            Log.w(TAG, "Append failed", t)
            stopInternal()
        }
    }

    /** Flush periodically so a crash still keeps most rows. */
    @Synchronized
    fun flush() {
        runCatching { writer?.flush() }
    }

    private fun fmt(v: Float?): String =
        if (v == null) "" else String.format(Locale.US, "%.3f", v)

    private fun bool(v: Boolean): String = if (v) "1" else "0"
}
