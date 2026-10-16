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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsReading(
    val speedMps: Float? = null,
    val hasFix: Boolean = false,
    val accuracyM: Float? = null
)

/**
 * Phone GPS speed via LocationManager (no Play Services).
 */
class GpsSpeedProvider(private val context: Context) : LocationListener {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _reading = MutableStateFlow(GpsReading())
    val reading: StateFlow<GpsReading> = _reading.asStateFlow()

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            _reading.value = GpsReading(hasFix = false)
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
        providers.firstOrNull()?.let { provider ->
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
        }
    }

    fun stop() {
        runCatching { locationManager.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        val hasSpeed = location.hasSpeed()
        val speed = if (hasSpeed) location.speed else null
        val accurate = !location.hasAccuracy() || location.accuracy <= 30f
        _reading.value = GpsReading(
            speedMps = speed,
            hasFix = accurate && (speed != null || location.accuracy <= 20f),
            accuracyM = if (location.hasAccuracy()) location.accuracy else null
        )
    }

    @Deprecated("Deprecated in API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) {
        _reading.value = _reading.value.copy(hasFix = false)
    }
}
