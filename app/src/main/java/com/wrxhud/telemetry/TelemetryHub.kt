package com.wrxhud.telemetry

import com.wrxhud.settings.HudPreferences
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide telemetry for phone UI and Android Auto.
 */
object TelemetryHub {
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val _preferences = MutableStateFlow(HudPreferences())
    val preferences: StateFlow<HudPreferences> = _preferences.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun publishSnapshot(value: VehicleSnapshot) {
        _snapshot.value = value
    }

    fun publishPreferences(value: HudPreferences) {
        _preferences.value = value
    }

    fun publishStatus(value: String) {
        _status.value = value
    }
}

enum class AppPage {
    HUD,
    NAVIGATION
}
