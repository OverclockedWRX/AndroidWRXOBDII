package com.wrxhud.telemetry

import android.app.Application
import com.wrxhud.gps.GpsReading
import com.wrxhud.gps.GpsSpeedProvider
import com.wrxhud.gps.SpeedLimitProvider
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.logging.CsvDataLogger
import com.wrxhud.logging.DataLogController
import com.wrxhud.logging.LogSample
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.MotionSensorProvider
import com.wrxhud.sensors.relativeTo
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.SettingsRepository
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Process-wide telemetry source of truth. [com.wrxhud.obd.ObdService] writes the raw
 * OBD frames, link status and vehicle info; the repository merges them with phone GPS
 * so the phone HUD and Android Auto both read the same display values, with or
 * without the phone UI open. Call [init] once from the Application.
 */
object TelemetryRepository {
    private lateinit var app: Application
    private lateinit var appScope: CoroutineScope
    private var initialized = false

    lateinit var settings: SettingsRepository
        private set
    lateinit var gpsProvider: GpsSpeedProvider
        private set
    lateinit var motionProvider: MotionSensorProvider
        private set
    lateinit var speedLimitProvider: SpeedLimitProvider
        private set
    lateinit var dataLog: DataLogController
        private set

    // Raw, written by the service.
    private val _obd = MutableStateFlow(VehicleSnapshot())
    val obd: StateFlow<VehicleSnapshot> = _obd.asStateFlow()
    private val _obdStatus = MutableStateFlow("Idle")
    val obdStatus: StateFlow<String> = _obdStatus.asStateFlow()
    private val _rawVehicleInfo = MutableStateFlow(VehicleInfo())
    val rawVehicleInfo: StateFlow<VehicleInfo> = _rawVehicleInfo.asStateFlow()

    private val _pollStats = MutableStateFlow(com.wrxhud.obd.PollStats())
    val pollStats: StateFlow<com.wrxhud.obd.PollStats> = _pollStats.asStateFlow()

    // Derived display values.
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _vehicleInfo = MutableStateFlow(VehicleInfo())
    val vehicleInfo: StateFlow<VehicleInfo> = _vehicleInfo.asStateFlow()

    private val _preferences = MutableStateFlow(HudPreferences())
    val preferences: StateFlow<HudPreferences> = _preferences.asStateFlow()

    val gps: StateFlow<GpsReading> get() = gpsProvider.reading
    val rawMotion: StateFlow<MotionReading> get() = motionProvider.reading
    val speedLimit: StateFlow<SpeedLimitReading> get() = speedLimitProvider.reading

    private var motionConsumers = 0
    private var gpsMode: com.wrxhud.gps.GpsSourceMode? = null
    private var gpsAddress: String? = null
    private var gpsStarted = false

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        settings = SettingsRepository(application)
        gpsProvider = GpsSpeedProvider(application)
        motionProvider = MotionSensorProvider(application)
        speedLimitProvider = SpeedLimitProvider(appScope, application)
        dataLog = DataLogController(
            logger = CsvDataLogger(application),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            sample = ::currentLogSample,
            onActiveChanged = { active -> if (active) acquireMotion() else releaseMotion() }
        )

        speedLimitProvider.start(gpsProvider.reading, settings.preferencesFlow)

        appScope.launch {
            settings.preferencesFlow.collect { prefs ->
                _preferences.value = prefs
                if (prefs.onboardingComplete) ensureGps(prefs)
            }
        }
        // Display merge. Four inputs on purpose: raw frame, merge-relevant prefs, GPS, status.
        val mergePrefs = settings.preferencesFlow
            .map { MergePrefs(it.useSimulator, it.toAlarmThresholds()) }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, MergePrefs())
        appScope.launch {
            combine(_obd, mergePrefs, gpsProvider.reading, _obdStatus) { raw, mp, g, st ->
                Triple(TelemetryMerge.displaySnapshot(raw, mp, g), TelemetryMerge.displayStatus(raw, mp, st), raw)
            }.collect { (snap, status, _) ->
                _snapshot.value = snap
                _status.value = status
                _vehicleInfo.value = TelemetryMerge.displayVehicleInfo(_rawVehicleInfo.value, snap)
            }
        }
        appScope.launch {
            _rawVehicleInfo.collect { info ->
                _vehicleInfo.value = TelemetryMerge.displayVehicleInfo(info, _snapshot.value)
            }
        }
    }

    // ---- writers (service) ----
    fun publishObd(frame: VehicleSnapshot) { _obd.value = frame }
    fun publishObdStatus(status: String) { _obdStatus.value = status }
    fun publishVehicleInfo(info: VehicleInfo) { _rawVehicleInfo.value = info }
    fun publishPollStats(stats: com.wrxhud.obd.PollStats) { _pollStats.value = stats }

    /** Last raw OBD frame (service side use, e.g. phone-only idle). */
    fun currentObd(): VehicleSnapshot = _obd.value

    // ---- GPS / motion lifecycle ----
    private fun ensureGps(prefs: HudPreferences) {
        val changed = gpsMode != prefs.gpsSourceMode || gpsAddress != prefs.bluetoothGpsAddress
        if (!gpsStarted || changed) {
            gpsMode = prefs.gpsSourceMode
            gpsAddress = prefs.bluetoothGpsAddress
            gpsStarted = true
            gpsProvider.start(
                mode = prefs.gpsSourceMode,
                bluetoothAddress = prefs.bluetoothGpsAddress.ifBlank { null }
            )
        }
    }

    @Synchronized
    fun acquireMotion() {
        if (motionConsumers++ == 0) motionProvider.start()
    }

    @Synchronized
    fun releaseMotion() {
        if (motionConsumers > 0 && --motionConsumers == 0) motionProvider.stop()
    }

    private fun currentLogSample(): LogSample {
        val prefs = _preferences.value
        return LogSample(
            snapshot = _snapshot.value,
            motion = motionProvider.reading.value.relativeTo(
                calib = prefs.mountCalibration(),
                calibrated = prefs.motionCalibrated
            ),
            speedLimit = speedLimitProvider.reading.value
        )
    }
}
