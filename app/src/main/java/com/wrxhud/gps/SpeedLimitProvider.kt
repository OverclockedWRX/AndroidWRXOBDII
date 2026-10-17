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
import org.json.JSONArray
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
 * Looks up posted speed limits from OpenStreetMap via the public Overpass API
 * (`maxspeed` tags on nearby highways). No API key required.
 *
 * Coverage varies by region; untagged roads return N/A.
 */
class SpeedLimitProvider(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SpeedLimit"
        private const val MIN_MOVE_M = 40.0
        private const val POLL_INTERVAL_MS = 15_000L
        private const val USER_AGENT = "WRXRaceHUD/0.17 (private; OSM Overpass speed-limit)"
        private val OVERPASS_ENDPOINTS = listOf(
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.osm.ch/api/interpreter"
        )
        private val SEARCH_RADII_M = listOf(30, 60, 120)
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
                pref.showSpeedLimit to pref.speedUnit
            }.distinctUntilChanged()

            enabledFlow.collectLatest { (enabled, speedUnit) ->
                if (!enabled) {
                    _reading.value = SpeedLimitReading(status = "Off")
                    resetCache()
                    return@collectLatest
                }

                while (isActive) {
                    val g = gps.value
                    val lat = g.latitude
                    val lng = g.longitude
                    if (!g.hasFix || lat == null || lng == null) {
                        if (_reading.value.limit == null) {
                            _reading.value = SpeedLimitReading(unit = speedUnit, status = "NO GPS")
                        }
                        delay(2_000L)
                        continue
                    }
                    val moved = lastLat == null ||
                        haversineM(lastLat!!, lastLng!!, lat, lng) >= MIN_MOVE_M
                    if (moved || _reading.value.limit == null || _reading.value.unit != speedUnit) {
                        fetchLimit(lat, lng, speedUnit)
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

    private suspend fun fetchLimit(lat: Double, lng: Double, unit: SpeedUnit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { queryOverpass(lat, lng, unit) }
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
                    status = "ERR"
                )
            }
        )
    }

    private fun queryOverpass(lat: Double, lng: Double, unit: SpeedUnit): SpeedLimitReading {
        var sawHighway = false
        for (radius in SEARCH_RADII_M) {
            val elements = overpassWays(lat, lng, radius)
            if (elements.length() == 0) continue

            val ranked = rankWays(elements, lat, lng)
            if (ranked.isNotEmpty()) sawHighway = true

            val withLimit = ranked.firstOrNull { it.limitKph != null }
            if (withLimit != null) {
                val placeId = withLimit.wayId
                if (placeId == lastPlaceId &&
                    _reading.value.limit != null &&
                    _reading.value.unit == unit
                ) {
                    return _reading.value.copy(status = null)
                }
                val limit = convertLimit(withLimit.limitKph!!, unit)
                Log.i(
                    TAG,
                    "OSM way/${withLimit.wayId} maxspeed=${withLimit.rawMaxspeed} " +
                        "→ $limit ${unit.name} @ ${"%.5f".format(Locale.US, lat)}," +
                        "${"%.5f".format(Locale.US, lng)} r=${radius}m"
                )
                return SpeedLimitReading(
                    limit = limit,
                    unit = unit,
                    placeId = placeId,
                    status = null
                )
            }
        }
        return SpeedLimitReading(
            unit = unit,
            status = if (sawHighway) "N/A" else "NO RD"
        )
    }

    private fun overpassWays(lat: Double, lng: Double, radiusM: Int): JSONArray {
        val query = buildString {
            append("[out:json][timeout:10];")
            append("way[\"highway\"](around:")
            append(radiusM)
            append(',')
            append(String.format(Locale.US, "%.6f", lat))
            append(',')
            append(String.format(Locale.US, "%.6f", lng))
            append(");")
            append("out tags center;")
        }
        var lastError: Exception? = null
        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                val json = httpPostForm(endpoint, "data" to query)
                val remark = json.optString("remark")
                if (remark.contains("error", ignoreCase = true)) {
                    error(remark.take(80))
                }
                return json.optJSONArray("elements") ?: JSONArray()
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Overpass failed at $endpoint: ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("Overpass unavailable")
    }

    private data class RankedWay(
        val wayId: String,
        val distanceM: Double,
        val rawMaxspeed: String?,
        val limitKph: Double?,
        val highway: String
    )

    private fun rankWays(elements: JSONArray, lat: Double, lng: Double): List<RankedWay> {
        val out = ArrayList<RankedWay>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            if (el.optString("type") != "way") continue
            val tags = el.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway").takeIf { it.isNotBlank() } ?: continue
            if (highway in IGNORED_HIGHWAYS) continue
            val center = el.optJSONObject("center")
            val cLat = center?.optDouble("lat") ?: continue
            val cLng = center?.optDouble("lon") ?: continue
            val raw = firstMaxspeed(tags)
            out += RankedWay(
                wayId = "osm:${el.optLong("id")}",
                distanceM = haversineM(lat, lng, cLat, cLng),
                rawMaxspeed = raw,
                limitKph = raw?.let { parseMaxspeedToKph(it) },
                highway = highway
            )
        }
        return out.sortedWith(
            compareBy<RankedWay> { if (it.limitKph != null) 0 else 1 }
                .thenBy { it.distanceM }
                .thenBy { highwayRank(it.highway) }
        )
    }

    private fun firstMaxspeed(tags: JSONObject): String? {
        val keys = listOf("maxspeed", "maxspeed:forward", "maxspeed:backward", "maxspeed:practical")
        for (key in keys) {
            val v = tags.optString(key).trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun httpPostForm(url: String, vararg form: Pair<String, String>): JSONObject {
        val body = form.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8.name())}=" +
                URLEncoder.encode(v, Charsets.UTF_8.name())
        }.toByteArray(Charsets.UTF_8)

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) error("HTTP $code empty body")
            if (code == 429 || code == 504 || code == 502) {
                error("Overpass busy (HTTP $code)")
            }
            if (code !in 200..299) {
                error("HTTP $code ${text.take(120)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun convertLimit(kph: Double, unit: SpeedUnit): Int {
        val value = when (unit) {
            SpeedUnit.MPH -> kph / 1.609344
            SpeedUnit.KPH -> kph
        }
        return value.toInt().coerceAtLeast(1)
    }

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

private val IGNORED_HIGHWAYS = setOf(
    "footway", "path", "steps", "cycleway", "bridleway", "pedestrian",
    "corridor", "platform", "proposed", "construction", "raceway", "track"
)

private fun highwayRank(highway: String): Int = when (highway) {
    "motorway", "motorway_link" -> 0
    "trunk", "trunk_link" -> 1
    "primary", "primary_link" -> 2
    "secondary", "secondary_link" -> 3
    "tertiary", "tertiary_link" -> 4
    "unclassified", "residential" -> 5
    "living_street", "service" -> 6
    else -> 7
}

/**
 * Convert an OSM `maxspeed` tag to km/h. Bare numbers are km/h per OSM convention;
 * explicit mph / knots are converted. Returns null for non-numeric / variable values.
 */
internal fun parseMaxspeedToKph(raw: String): Double? {
    val s = raw.trim().lowercase(Locale.US)
    if (s.isEmpty()) return null
    if (s in setOf(
            "none", "signals", "variable", "walk", "unknown", "unposted",
            "national", "default"
        ) || s.contains(':')
    ) {
        // Zone codes like DE:urban / CA:urban need regional tables — skip for now.
        return null
    }
    // "25 mph", "50 km/h", "30", "70"
    val mph = Regex("""^(\d+(?:\.\d+)?)\s*mph$""").matchEntire(s)
    if (mph != null) {
        return mph.groupValues[1].toDouble() * 1.609344
    }
    val kn = Regex("""^(\d+(?:\.\d+)?)\s*knots?$""").matchEntire(s)
    if (kn != null) {
        return kn.groupValues[1].toDouble() * 1.852
    }
    val kph = Regex("""^(\d+(?:\.\d+)?)\s*(?:km/h|kmh|kph)?$""").matchEntire(s)
    if (kph != null) {
        return kph.groupValues[1].toDouble()
    }
    return null
}
