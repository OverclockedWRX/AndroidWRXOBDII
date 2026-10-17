package com.wrxhud.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsSourceKind {
    NONE,
    BUILTIN,
    BLUETOOTH,
    USB
}

enum class GpsSourceMode {
    AUTO,
    BUILTIN,
    BLUETOOTH,
    USB;

    val label: String
        get() = when (this) {
            AUTO -> "Auto"
            BUILTIN -> "Built-in"
            BLUETOOTH -> "Bluetooth dongle"
            USB -> "USB dongle"
        }
}

data class GpsReading(
    val speedMps: Float? = null,
    val hasFix: Boolean = false,
    val accuracyM: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: GpsSourceKind = GpsSourceKind.NONE
)

/**
 * Aggregates built-in LocationManager GPS plus Bluetooth / USB NMEA dongles.
 */
class GpsSpeedProvider(private val context: Context) : LocationListener {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothGps = BluetoothNmeaGps(context, scope) { publishExternal(it) }
    private val usbGps = UsbNmeaGps(context, scope) { publishExternal(it) }

    private val _reading = MutableStateFlow(GpsReading())
    val reading: StateFlow<GpsReading> = _reading.asStateFlow()

    private var mode: GpsSourceMode = GpsSourceMode.AUTO
    private var bluetoothAddress: String? = null
    private var builtinActive = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBuiltInGpsHardware(): Boolean {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) return false
        return runCatching {
            locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    fun start(
        mode: GpsSourceMode = GpsSourceMode.AUTO,
        bluetoothAddress: String? = null
    ) {
        this.mode = mode
        this.bluetoothAddress = bluetoothAddress
        stopSources()
        _reading.value = GpsReading()

        when (mode) {
            GpsSourceMode.BUILTIN -> startBuiltin()
            GpsSourceMode.BLUETOOTH -> bluetoothGps.start(bluetoothAddress)
            GpsSourceMode.USB -> usbGps.start()
            GpsSourceMode.AUTO -> {
                if (hasBuiltInGpsHardware()) {
                    startBuiltin()
                }
                // Always arm external sources as fallback for tablets / AAUs without GNSS.
                bluetoothGps.start(bluetoothAddress)
                usbGps.start()
            }
        }
    }

    fun stop() {
        stopSources()
    }

    fun destroy() {
        stopSources()
        scope.cancel()
    }

    private fun stopSources() {
        stopBuiltin()
        bluetoothGps.stop()
        usbGps.stop()
    }

    @SuppressLint("MissingPermission")
    private fun startBuiltin() {
        if (!hasPermission()) {
            _reading.value = GpsReading(source = GpsSourceKind.BUILTIN)
            return
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { locationManager.isProviderEnabled(it) }

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                200L,
                0.5f,
                this,
                Looper.getMainLooper()
            )
        }
        builtinActive = providers.isNotEmpty()
        providers.firstOrNull()?.let { provider ->
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
        }
    }

    private fun stopBuiltin() {
        if (builtinActive) {
            runCatching { locationManager.removeUpdates(this) }
            builtinActive = false
        }
    }

    private fun publishExternal(reading: GpsReading) {
        val current = _reading.value
        // Prefer a live built-in fix when present; otherwise take external NMEA.
        val builtinHasLiveFix =
            current.source == GpsSourceKind.BUILTIN && current.hasFix && current.speedMps != null
        if (builtinHasLiveFix && mode == GpsSourceMode.AUTO) return
        if (reading.speedMps != null || reading.hasFix) {
            _reading.value = reading
        } else if (current.source == GpsSourceKind.NONE || current.source == reading.source) {
            _reading.value = reading
        }
    }

    override fun onLocationChanged(location: Location) {
        val hasSpeed = location.hasSpeed()
        val speed = if (hasSpeed) location.speed else null
        val accurate = !location.hasAccuracy() || location.accuracy <= 30f
        val reading = GpsReading(
            speedMps = speed,
            hasFix = accurate && (speed != null || location.accuracy <= 20f),
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            latitude = location.latitude,
            longitude = location.longitude,
            source = GpsSourceKind.BUILTIN
        )
        if (mode == GpsSourceMode.BUILTIN || mode == GpsSourceMode.AUTO) {
            if (reading.hasFix || _reading.value.source == GpsSourceKind.BUILTIN ||
                _reading.value.source == GpsSourceKind.NONE
            ) {
                _reading.value = reading
            }
        }
    }

    @Deprecated("Deprecated in API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        if (_reading.value.source == GpsSourceKind.BUILTIN) {
            _reading.value = _reading.value.copy(hasFix = false)
        }
    }
}
