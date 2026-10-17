package com.wrxhud.gps

import android.content.Context
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
 * Looks up posted speed limits from OpenStreetMap via public Overpass mirrors
 * (`maxspeed` tags on nearby highways). No API key required.
 *
 * Results are cached on-device so the last known limit still shows during outages.
 */
class SpeedLimitProvider(
    private val scope: CoroutineScope,
    context: Context
) {
    companion object {
        private const val TAG = "SpeedLimit"
        private const val MIN_MOVE_M = 40.0
        private const val POLL_INTERVAL_MS = 12_000L
        private const val SEARCH_RADIUS_M = 100
        private const val CACHE_HIT_M = 80.0
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val FALLBACK_CONNECT_MS = 5_000
        private const val FALLBACK_READ_MS = 5_000
        private const val USER_AGENT = "WRXRaceHUD/0.18 (private; OSM Overpass speed-limit)"

        /** Fast mirrors first; private.coffee answered in ~0.5s in testing. */
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass.private.coffee/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.osm.ch/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )
    }

    private val appContext = context.applicationContext
    private val diskCache = SpeedLimitDiskCache(appContext)

    private val _reading = MutableStateFlow(SpeedLimitReading(status = "Off"))
    val reading: StateFlow<SpeedLimitReading> = _reading.asStateFlow()

    private var job: Job? = null
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastPlaceId: String? = null
    private var preferredEndpoint: String? = null

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
                    resetSession()
                    return@collectLatest
                }

                while (isActive) {
                    val g = gps.value
                    val lat = g.latitude
                    val lng = g.longitude
                    if (!g.hasFix || lat == null || lng == null) {
                        if (_reading.value.limit == null) {
                            val cached = diskCache.nearest(lat = null, lng = null, unit = speedUnit)
                            _reading.value = cached?.toReading(status = "CACHE")
                                ?: SpeedLimitReading(unit = speedUnit, status = "NO GPS")
                        }
                        delay(2_000L)
                        continue
                    }

                    // Instant UI from disk while we refresh.
                    val nearbyCache = diskCache.nearest(lat, lng, speedUnit)
                    if (nearbyCache != null && _reading.value.limit == null) {
                        _reading.value = nearbyCache.toReading(status = null)
                        lastPlaceId = nearbyCache.placeId
                    }

                    val moved = lastLat == null ||
                        haversineM(lastLat!!, lastLng!!, lat, lng) >= MIN_MOVE_M
                    val needsFresh = moved ||
                        _reading.value.limit == null ||
                        _reading.value.unit != speedUnit ||
                        _reading.value.status == "ERR" ||
                        _reading.value.status == "CACHE"
                    if (needsFresh) {
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

    private fun resetSession() {
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
                if (reading.limit != null || reading.status == "N/A" || reading.status == "NO RD") {
                    diskCache.put(lat, lng, reading)
                }
            },
            onFailure = { t ->
                Log.w(TAG, "Speed limit lookup failed", t)
                val cached = diskCache.nearest(lat, lng, unit)
                    ?: diskCache.nearest(null, null, unit)
                if (cached != null) {
                    Log.i(
                        TAG,
                        "Using cached limit=${cached.limit} ${cached.unit} " +
                            "from ${"%.5f".format(Locale.US, cached.lat)}," +
                            "${"%.5f".format(Locale.US, cached.lng)}"
                    )
                    lastLat = lat
                    lastLng = lng
                    lastPlaceId = cached.placeId
                    _reading.value = cached.toReading(status = "CACHE")
                } else {
                    _reading.value = SpeedLimitReading(
                        limit = _reading.value.limit,
                        unit = unit,
                        placeId = lastPlaceId,
                        status = "ERR"
                    )
                }
            }
        )
    }

    private fun queryOverpass(lat: Double, lng: Double, unit: SpeedUnit): SpeedLimitReading {
        val elements = overpassWays(lat, lng, SEARCH_RADIUS_M)
        val ranked = rankWays(elements, lat, lng)
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
                "OSM ${withLimit.wayId} maxspeed=${withLimit.rawMaxspeed} " +
                    "→ $limit ${unit.name} dist=${withLimit.distanceM.toInt()}m " +
                    "@ ${"%.5f".format(Locale.US, lat)},${"%.5f".format(Locale.US, lng)}"
            )
            return SpeedLimitReading(
                limit = limit,
                unit = unit,
                placeId = placeId,
                status = null
            )
        }
        return SpeedLimitReading(
            unit = unit,
            status = if (ranked.isNotEmpty()) "N/A" else "NO RD"
        )
    }

    private fun overpassWays(lat: Double, lng: Double, radiusM: Int): JSONArray {
        val query = buildString {
            append("[out:json][timeout:15];")
            append("way[\"highway\"](around:")
            append(radiusM)
            append(',')
            append(String.format(Locale.US, "%.6f", lat))
            append(',')
            append(String.format(Locale.US, "%.6f", lng))
            append(");")
            append("out tags center;")
        }
        val endpoints = endpointsInOrder()
        var lastError: Exception? = null
        for ((index, endpoint) in endpoints.withIndex()) {
            val primary = index == 0
            try {
                val json = httpPostForm(
                    endpoint,
                    connectMs = if (primary) CONNECT_TIMEOUT_MS else FALLBACK_CONNECT_MS,
                    readMs = if (primary) READ_TIMEOUT_MS else FALLBACK_READ_MS,
                    "data" to query
                )
                val remark = json.optString("remark")
                if (remark.contains("error", ignoreCase = true)) {
                    error(remark.take(80))
                }
                preferredEndpoint = endpoint
                Log.i(TAG, "Overpass OK via $endpoint")
                return json.optJSONArray("elements") ?: JSONArray()
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Overpass failed at $endpoint: ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("Overpass unavailable")
    }

    private fun endpointsInOrder(): List<String> {
        val preferred = preferredEndpoint
        return if (preferred != null && preferred in OVERPASS_ENDPOINTS) {
            listOf(preferred) + OVERPASS_ENDPOINTS.filter { it != preferred }
        } else {
            OVERPASS_ENDPOINTS
        }
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

    private fun httpPostForm(
        url: String,
        connectMs: Int,
        readMs: Int,
        vararg form: Pair<String, String>
    ): JSONObject {
        val body = form.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8.name())}=" +
                URLEncoder.encode(v, Charsets.UTF_8.name())
        }.toByteArray(Charsets.UTF_8)

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
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

    private fun SpeedLimitDiskCache.Entry.toReading(status: String?): SpeedLimitReading =
        SpeedLimitReading(
            limit = limit,
            unit = unit,
            placeId = placeId,
            status = status ?: this.status
        )

    /**
     * Persists recent successful lookups so outages still show a limit.
     * Entries expire after [CACHE_TTL_MS]; nearest within [CACHE_HIT_M] wins.
     */
    private class SpeedLimitDiskCache(context: Context) {
        private val prefs = context.getSharedPreferences("speed_limit_cache", Context.MODE_PRIVATE)

        data class Entry(
            val lat: Double,
            val lng: Double,
            val limit: Int?,
            val unit: SpeedUnit,
            val placeId: String?,
            val status: String?,
            val savedAtMs: Long
        )

        fun put(lat: Double, lng: Double, reading: SpeedLimitReading) {
            val key = cellKey(lat, lng, reading.unit)
            val json = JSONObject()
                .put("lat", lat)
                .put("lng", lng)
                .put("limit", reading.limit ?: JSONObject.NULL)
                .put("unit", reading.unit.name)
                .put("placeId", reading.placeId)
                .put("status", reading.status)
                .put("savedAtMs", System.currentTimeMillis())
            prefs.edit().putString(key, json.toString()).apply()
            prune(reading.unit)
        }

        fun nearest(lat: Double?, lng: Double?, unit: SpeedUnit): Entry? {
            val now = System.currentTimeMillis()
            var best: Entry? = null
            var bestDist = Double.MAX_VALUE
            for ((_, raw) in prefs.all) {
                val text = raw as? String ?: continue
                val e = parse(text) ?: continue
                if (e.unit != unit) continue
                if (now - e.savedAtMs > CACHE_TTL_MS) continue
                if (lat == null || lng == null) {
                    if (best == null || e.savedAtMs > best.savedAtMs) best = e
                    continue
                }
                val d = haversineStatic(lat, lng, e.lat, e.lng)
                if (d <= CACHE_HIT_M && d < bestDist) {
                    best = e
                    bestDist = d
                }
            }
            return best
        }

        private fun prune(unit: SpeedUnit) {
            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            var changed = false
            val keep = ArrayList<Pair<String, Entry>>()
            for ((key, raw) in prefs.all) {
                val text = raw as? String ?: continue
                val e = parse(text)
                if (e == null || now - e.savedAtMs > CACHE_TTL_MS) {
                    editor.remove(key)
                    changed = true
                } else if (e.unit == unit) {
                    keep += key to e
                }
            }
            if (keep.size > 40) {
                keep.sortedByDescending { it.second.savedAtMs }
                    .drop(40)
                    .forEach {
                        editor.remove(it.first)
                        changed = true
                    }
            }
            if (changed) editor.apply()
        }

        private fun parse(text: String): Entry? = runCatching {
            val o = JSONObject(text)
            Entry(
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                limit = if (o.isNull("limit")) null else o.getInt("limit"),
                unit = SpeedUnit.valueOf(o.getString("unit")),
                placeId = o.optString("placeId").takeIf { it.isNotBlank() },
                status = o.optString("status").takeIf { it.isNotBlank() },
                savedAtMs = o.getLong("savedAtMs")
            )
        }.getOrNull()

        private fun cellKey(lat: Double, lng: Double, unit: SpeedUnit): String {
            // ~110 m cells
            val la = String.format(Locale.US, "%.3f", lat)
            val lo = String.format(Locale.US, "%.3f", lng)
            return "c:$la:$lo:${unit.name}"
        }
    }
}

private fun haversineStatic(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
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
