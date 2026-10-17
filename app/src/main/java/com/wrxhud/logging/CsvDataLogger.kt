package com.wrxhud.logging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Appends HUD telemetry rows to a CSV in the public Downloads folder
 * (`Internal storage/Download/`) via MediaStore. Visible in Files / Downloads
 * without a storage permission on Android 10+.
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
            "oil_temp_source",
            "iat_c",
            "boost_psi",
            "afr",
            "oil_pressure_ok",
            "battery_v",
            "fuel_pct",
            "throttle_pct",
            "accel_pedal_pct",
            "brake_pct",
            "mil",
            "knock_active",
            "dam_low",
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
    private var mediaUri: Uri? = null
    private var displayName: String? = null

    /**
     * Starts a new log in Downloads. Returns the display file name on success.
     */
    @Synchronized
    fun start(): String? {
        if (_recording.value) return displayName
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "wrx_log_$stamp.csv"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            val stream = resolver.openOutputStream(uri, "w")
                ?: error("Could not open Downloads output stream")
            val w = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))
            w.write(HEADER)
            w.newLine()
            w.flush()
            writer = w
            mediaUri = uri
            displayName = name
            _activeFileName.value = name
            _recording.value = true
            Log.i(TAG, "Logging started: Download/$name ($uri)")
            name
        }.onFailure { t ->
            Log.e(TAG, "Failed to start log in Downloads", t)
            stopInternal(publish = false)
        }.getOrNull()
    }

    @Synchronized
    fun stop(): String? {
        val name = displayName
        stopInternal(publish = true)
        if (name != null) Log.i(TAG, "Logging stopped: Download/$name")
        return name
    }

    private fun stopInternal(publish: Boolean) {
        runCatching { writer?.flush(); writer?.close() }
        writer = null
        val uri = mediaUri
        mediaUri = null
        if (publish && uri != null) {
            runCatching {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                appContext.contentResolver.update(uri, done, null, null)
            }.onFailure { t ->
                Log.w(TAG, "Failed to clear IS_PENDING on $uri", t)
            }
        } else if (!publish && uri != null) {
            // Failed start — remove incomplete entry.
            runCatching { appContext.contentResolver.delete(uri, null, null) }
        }
        displayName = null
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
            snapshot.oilTempSource.orEmpty(),
            fmt(snapshot.intakeAirTempC),
            fmt(snapshot.boostPsi),
            fmt(snapshot.afr),
            snapshot.oilPressureOk?.let { if (it) "1" else "0" }.orEmpty(),
            fmt(snapshot.batteryVolts),
            fmt(snapshot.fuelLevelPercent),
            fmt(snapshot.throttlePercent),
            fmt(snapshot.acceleratorPercent),
            fmt(snapshot.brakePercent),
            snapshot.milOn?.let { if (it) "1" else "0" }.orEmpty(),
            snapshot.knockActive?.let { if (it) "1" else "0" }.orEmpty(),
            snapshot.damLow?.let { if (it) "1" else "0" }.orEmpty(),
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
            stopInternal(publish = true)
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
