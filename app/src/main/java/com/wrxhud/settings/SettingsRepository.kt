package com.wrxhud.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hud_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val gauges = stringSetPreferencesKey("gauges")
        val lamps = stringSetPreferencesKey("lamps")
        val tempUnit = stringPreferencesKey("temp_unit")
        val speedUnit = stringPreferencesKey("speed_unit")
        val pressureUnit = stringPreferencesKey("pressure_unit")
        val redline = intPreferencesKey("redline")
        val shiftLights = stringPreferencesKey("shift_lights")
        val useSpt = booleanPreferencesKey("use_spt")
        val tireWidth = intPreferencesKey("tire_width")
        val tireAspect = intPreferencesKey("tire_aspect")
        val wheelIn = intPreferencesKey("wheel_in")
        val useSim = booleanPreferencesKey("use_sim")
        val coolantAmber = floatPreferencesKey("coolant_amber")
        val coolantRed = floatPreferencesKey("coolant_red")
        val oilAmber = floatPreferencesKey("oil_amber")
        val oilRed = floatPreferencesKey("oil_red")
        val iatAmber = floatPreferencesKey("iat_amber")
        val iatRed = floatPreferencesKey("iat_red")
        val boostAmber = floatPreferencesKey("boost_amber")
        val boostRed = floatPreferencesKey("boost_red")
        val lowFuel = floatPreferencesKey("low_fuel")
        val lowBatt = floatPreferencesKey("low_batt")
    }

    val preferencesFlow: Flow<HudPreferences> = context.dataStore.data.map { it.toHudPreferences() }

    suspend fun update(transform: (HudPreferences) -> HudPreferences) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toHudPreferences())
            prefs[Keys.onboarding] = next.onboardingComplete
            prefs[Keys.gauges] = next.enabledGauges.map { it.name }.toSet()
            prefs[Keys.lamps] = next.enabledLamps.map { it.name }.toSet()
            prefs[Keys.tempUnit] = next.tempUnit.name
            prefs[Keys.speedUnit] = next.speedUnit.name
            prefs[Keys.pressureUnit] = next.pressureUnit.name
            prefs[Keys.redline] = next.redlineRpm
            prefs[Keys.shiftLights] = next.shiftLightStarts.joinToString(",")
            prefs[Keys.useSpt] = next.useSptCvt
            prefs[Keys.tireWidth] = next.tireWidthMm
            prefs[Keys.tireAspect] = next.tireAspect
            prefs[Keys.wheelIn] = next.wheelDiameterIn
            prefs[Keys.useSim] = next.useSimulator
            prefs[Keys.coolantAmber] = next.coolantAmberC
            prefs[Keys.coolantRed] = next.coolantRedC
            prefs[Keys.oilAmber] = next.oilTempAmberC
            prefs[Keys.oilRed] = next.oilTempRedC
            prefs[Keys.iatAmber] = next.iatAmberC
            prefs[Keys.iatRed] = next.iatRedC
            prefs[Keys.boostAmber] = next.boostAmberPsi
            prefs[Keys.boostRed] = next.boostRedPsi
            prefs[Keys.lowFuel] = next.lowFuelPercent
            prefs[Keys.lowBatt] = next.lowBatteryVolts
        }
    }

    private fun Preferences.toHudPreferences(): HudPreferences {
        val defaults = HudPreferences()
        return HudPreferences(
            onboardingComplete = this[Keys.onboarding] ?: defaults.onboardingComplete,
            enabledGauges = this[Keys.gauges]
                ?.mapNotNull { runCatching { GaugeId.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: defaults.enabledGauges,
            enabledLamps = this[Keys.lamps]
                ?.mapNotNull { runCatching { LampId.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: defaults.enabledLamps,
            tempUnit = this[Keys.tempUnit]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
                ?: defaults.tempUnit,
            speedUnit = this[Keys.speedUnit]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: defaults.speedUnit,
            pressureUnit = this[Keys.pressureUnit]
                ?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() }
                ?: defaults.pressureUnit,
            redlineRpm = this[Keys.redline] ?: defaults.redlineRpm,
            shiftLightStarts = this[Keys.shiftLights]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.size == 10 }
                ?: defaults.shiftLightStarts,
            useSptCvt = this[Keys.useSpt] ?: defaults.useSptCvt,
            tireWidthMm = this[Keys.tireWidth] ?: defaults.tireWidthMm,
            tireAspect = this[Keys.tireAspect] ?: defaults.tireAspect,
            wheelDiameterIn = this[Keys.wheelIn] ?: defaults.wheelDiameterIn,
            useSimulator = this[Keys.useSim] ?: defaults.useSimulator,
            coolantAmberC = this[Keys.coolantAmber] ?: defaults.coolantAmberC,
            coolantRedC = this[Keys.coolantRed] ?: defaults.coolantRedC,
            oilTempAmberC = this[Keys.oilAmber] ?: defaults.oilTempAmberC,
            oilTempRedC = this[Keys.oilRed] ?: defaults.oilTempRedC,
            iatAmberC = this[Keys.iatAmber] ?: defaults.iatAmberC,
            iatRedC = this[Keys.iatRed] ?: defaults.iatRedC,
            boostAmberPsi = this[Keys.boostAmber] ?: defaults.boostAmberPsi,
            boostRedPsi = this[Keys.boostRed] ?: defaults.boostRedPsi,
            lowFuelPercent = this[Keys.lowFuel] ?: defaults.lowFuelPercent,
            lowBatteryVolts = this[Keys.lowBatt] ?: defaults.lowBatteryVolts
        )
    }
}
