package com.wrxhud.hud

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wrxhud.gps.GpsSpeedProvider
import com.wrxhud.gps.SpeedLimitProvider
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.logging.CsvDataLogger
import com.wrxhud.obd.DtcReadResult
import com.wrxhud.obd.ObdService
import com.wrxhud.obd.PidScanRow
import com.wrxhud.sensors.MotionMountCalibration
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.MotionSensorProvider
import com.wrxhud.sensors.relativeTo
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.SettingsRepository
import com.wrxhud.telemetry.TelemetryHub
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HudUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val preferences: HudPreferences = HudPreferences(),
    val connectionStatus: String = "Idle",
    val motion: MotionReading = MotionReading(),
    val speedLimit: SpeedLimitReading = SpeedLimitReading(),
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val runtimeStarted: Boolean = false,
    val logging: Boolean = false,
    val logFileName: String? = null
)

class HudViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val gps = GpsSpeedProvider(application)
    private val motion = MotionSensorProvider(application)
    private val speedLimitProvider = SpeedLimitProvider(viewModelScope, application)
    private val dataLogger = CsvDataLogger(application)

    private val _serviceSnapshot = MutableStateFlow(VehicleSnapshot())
    private val _vehicleInfo = MutableStateFlow(VehicleInfo())
    private val _status = MutableStateFlow("Idle")
    private val _runtimeStarted = MutableStateFlow(false)
    private var service: ObdService? = null
    private var bound = false
    private var logJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as ObdService.LocalBinder
            service = local.getService()
            bound = true
            viewModelScope.launch {
                service?.snapshot?.collect { _serviceSnapshot.value = it }
            }
            viewModelScope.launch {
                service?.status?.collect { _status.value = it }
            }
            viewModelScope.launch {
                service?.vehicleInfo?.collect { _vehicleInfo.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    val uiState: StateFlow<HudUiState> = combine(
        combine(
            _serviceSnapshot,
            settingsRepository.preferencesFlow,
            _status,
            gps.reading,
            _runtimeStarted
        ) { snap, prefs, status, gpsReading, runtimeStarted ->
            Triple(snap to prefs, status to gpsReading, runtimeStarted)
        },
        combine(motion.reading, speedLimitProvider.reading, dataLogger.recording, dataLogger.activeFileName) {
                motionReading, speedLimit, logging, logName ->
            Quad(motionReading, speedLimit, logging, logName)
        },
        _vehicleInfo
    ) { core, logSide, vehicleInfo ->
        val (snapPrefs, statusGps, runtimeStarted) = core
        val (snap, prefs) = snapPrefs
        val (status, gpsReading) = statusGps
        val (motionReading, speedLimit, logging, logName) = logSide
        val gpsSpeed = gpsReading.speedMps
        val gpsFix = gpsReading.hasFix

        val hasObdStream = when {
            prefs.useSimulator -> snap.usingSimulator
            else -> snap.obdConnected && !snap.usingSimulator
        }

        val displaySnap = if (hasObdStream) {
            snap.copy(
                gpsSpeedMps = gpsSpeed ?: snap.gpsSpeedMps,
                gpsHasFix = gpsFix,
                usingSimulator = prefs.useSimulator && snap.usingSimulator
            )
        } else {
            snap.phoneOnly(
                gpsSpeedMps = gpsSpeed,
                gpsHasFix = gpsFix,
                thresholds = prefs.toAlarmThresholds(),
                expectObd = !prefs.useSimulator
            )
        }

        val displayStatus = when {
            prefs.useSimulator && snap.usingSimulator -> status
            prefs.useSimulator -> "Starting simulator…"
            snap.obdConnected -> status
            status.isNotBlank() && status != "Stopped" && status != "Idle" -> status
            else -> "No OBD — GPS only"
        }

        TelemetryHub.publishSnapshot(displaySnap)
        TelemetryHub.publishPreferences(prefs)
        TelemetryHub.publishStatus(displayStatus)

        val calibratedMotion = motionReading.relativeTo(
            calib = prefs.mountCalibration(),
            calibrated = prefs.motionCalibrated
        )

        val displayVehicleInfo = vehicleInfo.copy(
            oilTempSource = vehicleInfo.oilTempSource ?: displaySnap.oilTempSource
        )

        HudUiState(
            snapshot = displaySnap,
            preferences = prefs,
            connectionStatus = displayStatus,
            motion = calibratedMotion,
            speedLimit = speedLimit,
            vehicleInfo = displayVehicleInfo,
            runtimeStarted = runtimeStarted,
            logging = logging,
            logFileName = logName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HudUiState())

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private var lastSimulatorSetting: Boolean? = null
    private var lastGpsMode: com.wrxhud.gps.GpsSourceMode? = null
    private var lastBluetoothGpsAddress: String? = null

    init {
        speedLimitProvider.start(gps.reading, settingsRepository.preferencesFlow)
        viewModelScope.launch {
            settingsRepository.preferencesFlow.collect { prefs ->
                if (prefs.onboardingComplete) {
                    startRuntimeIfNeeded(prefs)
                }
            }
        }
    }

    fun startRuntimeIfNeeded(preferences: HudPreferences) {
        if (!preferences.onboardingComplete) return
        // Attitude sensors must run before mount calibration completes.
        ensureMotionStarted()
        val gpsChanged = lastGpsMode != preferences.gpsSourceMode ||
            lastBluetoothGpsAddress != preferences.bluetoothGpsAddress
        if (!_runtimeStarted.value || gpsChanged) {
            lastGpsMode = preferences.gpsSourceMode
            lastBluetoothGpsAddress = preferences.bluetoothGpsAddress
            gps.start(
                mode = preferences.gpsSourceMode,
                bluetoothAddress = preferences.bluetoothGpsAddress.ifBlank { null }
            )
            _runtimeStarted.value = true
        }
        // Keep OBD/sim off during the required mount-calibration screen.
        if (!preferences.motionCalibrated) return
        if (lastSimulatorSetting != preferences.useSimulator) {
            lastSimulatorSetting = preferences.useSimulator
            startObd(useSimulator = preferences.useSimulator)
        }
    }

    fun ensureMotionStarted() {
        motion.start()
    }

    /** Raw (uncalibrated) reading for the mount test UI. */
    val rawMotion: StateFlow<MotionReading> = motion.reading

    fun rawMotionNow(): MotionReading = motion.reading.value

    fun saveMountCalibration(calib: MotionMountCalibration) {
        updatePreferences { prefs ->
            prefs.copy(
                motionCalibrated = true,
                motionCalibPitchDeg = calib.pitchDeg,
                motionCalibRollDeg = calib.rollDeg,
                motionCalibYawDeg = calib.yawDeg,
                motionCalibGForce = calib.gForce
            )
        }
    }

    fun clearMountCalibration() {
        updatePreferences {
            it.copy(
                motionCalibrated = false,
                motionCalibPitchDeg = 0f,
                motionCalibRollDeg = 0f,
                motionCalibYawDeg = 0f,
                motionCalibGForce = 0f
            )
        }
    }

    fun startObd(useSimulator: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, ObdService::class.java).apply {
            action = ObdService.ACTION_START
            putExtra(ObdService.EXTRA_USE_SIMULATOR, useSimulator)
        }
        app.startForegroundService(intent)
        if (!bound) {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        if (!useSimulator) {
            _serviceSnapshot.value = VehicleSnapshot()
        }
    }

    /**
     * Start / restart OBD during the setup wizard so a live MX+ can be scanned
     * before onboarding + mount calibration complete.
     */
    fun ensureObdForSetup(useSimulator: Boolean) {
        if (lastSimulatorSetting != useSimulator || service == null || !bound) {
            lastSimulatorSetting = useSimulator
            startObd(useSimulator)
        }
    }

    fun stopObd() {
        val app = getApplication<Application>()
        val intent = Intent(app, ObdService::class.java).apply {
            action = ObdService.ACTION_STOP
        }
        app.startService(intent)
        _serviceSnapshot.value = VehicleSnapshot()
    }

    fun updatePreferences(transform: (HudPreferences) -> HudPreferences) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    /** Run a live PID scan on the connected MX+. Empty if simulator / not connected. */
    suspend fun scanPids(): List<PidScanRow> {
        return service?.scanPids().orEmpty()
    }

    suspend fun readDtcs(): DtcReadResult {
        return service?.readDtcs()
            ?: DtcReadResult(message = "Not connected — turn off simulator and link MX+")
    }

    suspend fun clearDtcs(): Boolean {
        return service?.clearDtcs() == true
    }

    suspend fun refreshVehicleInfo(): VehicleInfo {
        val info = service?.refreshVehicleInfo() ?: _vehicleInfo.value
        _vehicleInfo.value = info
        return info
    }

    fun isLiveObdConnected(): Boolean = service?.isLiveConnected() == true

    fun toggleDataLog() {
        if (dataLogger.recording.value) {
            stopDataLog()
        } else {
            startDataLog()
        }
    }

    fun startDataLog() {
        if (dataLogger.recording.value) return
        val started = dataLogger.start() ?: return
        logJob?.cancel()
        logJob = viewModelScope.launch {
            var ticks = 0
            while (isActive && dataLogger.recording.value) {
                val ui = uiState.value
                dataLogger.append(ui.snapshot, ui.motion, ui.speedLimit)
                ticks++
                if (ticks % 10 == 0) dataLogger.flush()
                delay(100L)
            }
        }
        android.util.Log.i("HudViewModel", "Data log → Download/$started")
    }

    fun stopDataLog() {
        logJob?.cancel()
        logJob = null
        dataLogger.stop()
    }

    override fun onCleared() {
        stopDataLog()
        speedLimitProvider.stop()
        gps.destroy()
        motion.stop()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
