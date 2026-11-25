package com.wrxhud.hud

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.obd.DtcReadResult
import com.wrxhud.obd.ObdService
import com.wrxhud.obd.PidScanRow
import com.wrxhud.sensors.MotionMountCalibration
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.relativeTo
import com.wrxhud.settings.HudPreferences
import com.wrxhud.telemetry.TelemetryRepository
import com.wrxhud.vehicle.VehicleInfo
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin UI-facing layer. Telemetry, preferences, GPS, speed limit and logging all live in
 * [TelemetryRepository] (fed by the service); this exposes them as separate flows so each
 * composable can collect only what it draws. No combine of everything at the root.
 */
class HudViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TelemetryRepository

    val preferences: StateFlow<HudPreferences> = repo.preferences
    val snapshot: StateFlow<VehicleSnapshot> = repo.snapshot
    val connectionStatus: StateFlow<String> = repo.status
    val vehicleInfo: StateFlow<VehicleInfo> = repo.vehicleInfo
    val speedLimit: StateFlow<SpeedLimitReading> = repo.speedLimit
    val logging: StateFlow<Boolean> = repo.dataLog.recording
    val logFileName: StateFlow<String?> = repo.dataLog.activeFileName

    /** Rarely-changing gates for the root composable. */
    val onboardingComplete: StateFlow<Boolean> = preferences
        .map { it.onboardingComplete }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val motionCalibrated: StateFlow<Boolean> = preferences
        .map { it.motionCalibrated }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Mount-calibrated motion for the HUD (collect only where it is drawn). */
    val motion: StateFlow<MotionReading> = combine(repo.rawMotion, preferences) { raw, prefs ->
        raw.relativeTo(calib = prefs.mountCalibration(), calibrated = prefs.motionCalibrated)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionReading())

    /** Raw (uncalibrated) motion; collect ONLY on the calibration screen. */
    val rawMotion: StateFlow<MotionReading> = repo.rawMotion

    private var service: ObdService? = null
    private var bound = false
    private var motionAcquired = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as ObdService.LocalBinder
            service = local.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    private var lastSimulatorSetting: Boolean? = null

    init {
        viewModelScope.launch {
            repo.preferences.collect { prefs ->
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
        // Keep OBD/sim off during the required mount-calibration screen.
        if (!preferences.motionCalibrated) return
        if (lastSimulatorSetting != preferences.useSimulator) {
            lastSimulatorSetting = preferences.useSimulator
            startObd(useSimulator = preferences.useSimulator)
        }
    }

    fun ensureMotionStarted() {
        if (!motionAcquired) {
            motionAcquired = true
            repo.acquireMotion()
        }
    }

    fun rawMotionNow(): MotionReading = repo.rawMotion.value

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
            repo.publishObd(VehicleSnapshot())
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
        repo.publishObd(VehicleSnapshot())
    }

    fun updatePreferences(transform: (HudPreferences) -> HudPreferences) {
        viewModelScope.launch {
            repo.settings.update(transform)
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
        val info = service?.refreshVehicleInfo() ?: repo.rawVehicleInfo.value
        repo.publishVehicleInfo(info)
        return info
    }

    fun isLiveObdConnected(): Boolean = service?.isLiveConnected() == true

    fun toggleDataLog() = repo.dataLog.toggle()

    override fun onCleared() {
        if (motionAcquired) {
            motionAcquired = false
            repo.releaseMotion()
        }
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
