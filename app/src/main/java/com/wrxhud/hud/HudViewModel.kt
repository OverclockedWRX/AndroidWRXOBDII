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
import com.wrxhud.obd.ObdService
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.SettingsRepository
import com.wrxhud.telemetry.TelemetryHub
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HudUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val preferences: HudPreferences = HudPreferences(),
    val connectionStatus: String = "Idle",
    val runtimeStarted: Boolean = false
)

class HudViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val gps = GpsSpeedProvider(application)

    private val _serviceSnapshot = MutableStateFlow(VehicleSnapshot())
    private val _status = MutableStateFlow("Idle")
    private val _runtimeStarted = MutableStateFlow(false)
    private var service: ObdService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as ObdService.LocalBinder
            service = local.getService()
            bound = true
            viewModelScope.launch {
                service?.snapshot?.collect {
                    _serviceSnapshot.value = it
                    TelemetryHub.publishSnapshot(mergeGps(it))
                }
            }
            viewModelScope.launch {
                service?.status?.collect {
                    _status.value = it
                    TelemetryHub.publishStatus(it)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    val uiState: StateFlow<HudUiState> = combine(
        _serviceSnapshot,
        settingsRepository.preferencesFlow,
        _status,
        gps.reading,
        _runtimeStarted
    ) { snap, prefs, status, gpsReading, runtimeStarted ->
        val merged = snap.copy(
            gpsSpeedMps = gpsReading.speedMps ?: snap.gpsSpeedMps,
            gpsHasFix = gpsReading.hasFix || snap.gpsHasFix
        )
        TelemetryHub.publishSnapshot(merged)
        TelemetryHub.publishPreferences(prefs)
        TelemetryHub.publishStatus(status)
        HudUiState(
            snapshot = merged,
            preferences = prefs,
            connectionStatus = status,
            runtimeStarted = runtimeStarted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HudUiState())

    private var lastSimulatorSetting: Boolean? = null

    /**
     * Starts GPS + OBD only after onboarding finishes and permissions are requested.
     */
    fun startRuntimeIfNeeded(preferences: HudPreferences) {
        if (!preferences.onboardingComplete) return
        if (!_runtimeStarted.value) {
            gps.start()
            _runtimeStarted.value = true
        }
        if (lastSimulatorSetting != preferences.useSimulator) {
            lastSimulatorSetting = preferences.useSimulator
            startObd(preferences.useSimulator)
        }
    }

    private fun mergeGps(snap: VehicleSnapshot): VehicleSnapshot {
        val reading = gps.reading.value
        return snap.copy(
            gpsSpeedMps = reading.speedMps ?: snap.gpsSpeedMps,
            gpsHasFix = reading.hasFix || snap.gpsHasFix
        )
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
    }

    fun stopObd() {
        val app = getApplication<Application>()
        val intent = Intent(app, ObdService::class.java).apply {
            action = ObdService.ACTION_STOP
        }
        app.startService(intent)
    }

    fun updatePreferences(transform: (HudPreferences) -> HudPreferences) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    override fun onCleared() {
        gps.stop()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
