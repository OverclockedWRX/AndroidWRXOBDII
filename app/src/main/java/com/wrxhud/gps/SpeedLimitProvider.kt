package com.wrxhud.gps

import android.util.Log
import com.wrxhud.settings.HudPreferences
import com.wrxhud.vehicle.SpeedUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SpeedLimitReading(
    /** Posted limit in [unit], or null if unknown / unavailable. */
    val limit: Int? = null,
    val unit: SpeedUnit = SpeedUnit.MPH,
    val placeId: String? = null,
    val status: String? = null
)

/**
 * Polls Google Roads API (nearestRoads → speedLimits) for the posted limit at the
 * current GPS position. Requires a Maps API key with Roads Speed Limits (Asset Tracking).
 */
class SpeedLimitProvider(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SpeedLimit"
        private const val MIN_MOVE_M = 40.0
        private const val POLL_INTERVAL_MS = 12_000L
        private const val NEAREST_URL = "https://roads.googleapis.com/v1/nearestRoads"
        private const val LIMITS_URL = "https://roads.googleapis.com/v1/speedLimits"
    }

    private val _reading = MutableStateFlow(SpeedLimitReading(status = "Off"))
    val reading: StateFlow<SpeedLimitReading> = _reading.asStateFlow()

    private var job: Job? = null
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastPlaceId: String? = null

    fun start(
        gps: StateFlow<GpsReading>,
        preferences: Flow<HudPreferences>
    ) {
        job?.cancel()
        job = scope.launch {
            val enabledFlow = preferences.map { pref ->
                Triple(pref.showSpeedLimit, pref.googleMapsApiKey.trim(), pref.speedUnit)
            }.distinctUntilChanged()

            enabledFlow.collectLatest { (enabled, apiKey, speedUnit) ->
                if (!enabled) {
                    _reading.value = SpeedLimitReading(status = "Off")
                    resetCache()
                    return@collectLatest
                }
                if (apiKey.isEmpty()) {
                    _reading.value = SpeedLimitReading(status = "Add Maps API key")
                    resetCache()
                    return@collectLatest
                }

                // Poll while this (enabled, key, unit) config stays active.
                while (isActive) {
                    val g = gps.value
                    val lat = g.latitude
                    val lng = g.longitude
                    if (!g.hasFix || lat == null || lng == null) {
                        if (_reading.value.limit == null) {
                            _reading.value = SpeedLimitReading(unit = speedUnit, status = "Waiting for GPS")
                        }
                        delay(2_000L)
                        continue
                    }
                    val moved = lastLat == null ||
                        haversineM(lastLat!!, lastLng!!, lat, lng) >= MIN_MOVE_M
                    if (moved || _reading.value.limit == null || _reading.value.unit != speedUnit) {
                        fetchLimit(lat, lng, apiKey, speedUnit)
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun resetCache() {
        lastLat = null
        lastLng = null
        lastPlaceId = null
    }

    private suspend fun fetchLimit(lat: Double, lng: Double, apiKey: String, unit: SpeedUnit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { queryRoadsApi(lat, lng, apiKey, unit) }
        }
        result.fold(
            onSuccess = { reading ->
                lastLat = lat
                lastLng = lng
                lastPlaceId = reading.placeId
                _reading.value = reading
            },
            onFailure = { t ->
                Log.w(TAG, "Speed limit lookup failed", t)
                _reading.value = SpeedLimitReading(
                    limit = _reading.value.limit,
                    unit = unit,
                    placeId = lastPlaceId,
                    status = t.message?.take(48) ?: "Lookup failed"
                )
            }
        )
    }

    private fun queryRoadsApi(lat: Double, lng: Double, apiKey: String, unit: SpeedUnit): SpeedLimitReading {
        val points = String.format(Locale.US, "%.6f,%.6f", lat, lng)
        val nearestUrl = "$NEAREST_URL?points=${enc(points)}&key=${enc(apiKey)}"
        val nearestJson = httpGetJson(nearestUrl)
        val snapped = nearestJson.optJSONArray("snappedPoints")
            ?: return SpeedLimitReading(unit = unit, status = "No road nearby")
        if (snapped.length() == 0) {
            return SpeedLimitReading(unit = unit, status = "No road nearby")
        }
        val placeId = snapped.getJSONObject(0).optString("placeId").takeIf { it.isNotBlank() }
            ?: return SpeedLimitReading(unit = unit, status = "No road nearby")

        if (placeId == lastPlaceId && _reading.value.limit != null && _reading.value.unit == unit) {
            return _reading.value.copy(status = null)
        }

        val unitsParam = if (unit == SpeedUnit.MPH) "MPH" else "KPH"
        val limitsUrl =
            "$LIMITS_URL?placeId=${enc(placeId)}&units=$unitsParam&key=${enc(apiKey)}"
        val limitsJson = httpGetJson(limitsUrl)
        val limits = limitsJson.optJSONArray("speedLimits")
        if (limits == null || limits.length() == 0) {
            val err = limitsJson.optString("error_message")
                .ifBlank { limitsJson.optJSONObject("error")?.optString("message").orEmpty() }
            return SpeedLimitReading(
                unit = unit,
                placeId = placeId,
                status = err.ifBlank { "No limit data" }.take(48)
            )
        }
        val entry = limits.getJSONObject(0)
        val limit = entry.optInt("speedLimit", -1).takeIf { it > 0 }
        return SpeedLimitReading(
            limit = limit,
            unit = unit,
            placeId = placeId,
            status = if (limit == null) "No limit data" else null
        )
    }

    private fun httpGetJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isBlank()) error("HTTP $code empty body")
            val json = JSONObject(body)
            if (code !in 200..299) {
                val msg = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("error_message").ifBlank { "HTTP $code" }
                error(msg)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
