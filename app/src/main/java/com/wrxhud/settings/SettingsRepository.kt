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

    val preferencesFlow: Flow<HudPreferences> = context.dataStore.data.map { prefs ->
        val defaults = HudPreferences()
        HudPreferences(
            enabledGauges = prefs[Keys.gauges]?.mapNotNull { runCatching { GaugeId.valueOf(it) }.getOrNull() }?.toSet()
                ?: defaults.enabledGauges,
            enabledLamps = prefs[Keys.lamps]?.mapNotNull { runCatching { LampId.valueOf(it) }.getOrNull() }?.toSet()
                ?: defaults.enabledLamps,
            tempUnit = prefs[Keys.tempUnit]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: defaults.tempUnit,
            speedUnit = prefs[Keys.speedUnit]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: defaults.speedUnit,
            pressureUnit = prefs[Keys.pressureUnit]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() }
                ?: defaults.pressureUnit,
            redlineRpm = prefs[Keys.redline] ?: defaults.redlineRpm,
            shiftLightStarts = prefs[Keys.shiftLights]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.size == 10 }
                ?: defaults.shiftLightStarts,
            useSptCvt = prefs[Keys.useSpt] ?: defaults.useSptCvt,
            tireWidthMm = prefs[Keys.tireWidth] ?: defaults.tireWidthMm,
            tireAspect = prefs[Keys.tireAspect] ?: defaults.tireAspect,
            wheelDiameterIn = prefs[Keys.wheelIn] ?: defaults.wheelDiameterIn,
            useSimulator = prefs[Keys.useSim] ?: defaults.useSimulator,
            coolantAmberC = prefs[Keys.coolantAmber] ?: defaults.coolantAmberC,
            coolantRedC = prefs[Keys.coolantRed] ?: defaults.coolantRedC,
            oilTempAmberC = prefs[Keys.oilAmber] ?: defaults.oilTempAmberC,
            oilTempRedC = prefs[Keys.oilRed] ?: defaults.oilTempRedC,
            iatAmberC = prefs[Keys.iatAmber] ?: defaults.iatAmberC,
            iatRedC = prefs[Keys.iatRed] ?: defaults.iatRedC,
            boostAmberPsi = prefs[Keys.boostAmber] ?: defaults.boostAmberPsi,
            boostRedPsi = prefs[Keys.boostRed] ?: defaults.boostRedPsi,
            lowFuelPercent = prefs[Keys.lowFuel] ?: defaults.lowFuelPercent,
            lowBatteryVolts = prefs[Keys.lowBatt] ?: defaults.lowBatteryVolts
        )
    }

    suspend fun update(transform: (HudPreferences) -> HudPreferences) {
        context.dataStore.edit { prefs ->
            val current = HudPreferences(
                enabledGauges = prefs[Keys.gauges]?.mapNotNull { runCatching { GaugeId.valueOf(it) }.getOrNull() }?.toSet()
                    ?: HudPreferences().enabledGauges,
                enabledLamps = prefs[Keys.lamps]?.mapNotNull { runCatching { LampId.valueOf(it) }.getOrNull() }?.toSet()
                    ?: HudPreferences().enabledLamps,
                tempUnit = prefs[Keys.tempUnit]?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() } ?: TempUnit.F,
                speedUnit = prefs[Keys.speedUnit]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: SpeedUnit.MPH,
                pressureUnit = prefs[Keys.pressureUnit]?.let { runCatching { PressureUnit.valueOf(it) }.getOrNull() }
                    ?: PressureUnit.PSI,
                redlineRpm = prefs[Keys.redline] ?: WrxProfileDefaults.redline,
                shiftLightStarts = prefs[Keys.shiftLights]
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.takeIf { it.size == 10 }
                    ?: HudPreferences().shiftLightStarts,
                useSptCvt = prefs[Keys.useSpt] ?: false,
                tireWidthMm = prefs[Keys.tireWidth] ?: 245,
                tireAspect = prefs[Keys.tireAspect] ?: 40,
                wheelDiameterIn = prefs[Keys.wheelIn] ?: 18,
                useSimulator = prefs[Keys.useSim] ?: true,
                coolantAmberC = prefs[Keys.coolantAmber] ?: 105f,
                coolantRedC = prefs[Keys.coolantRed] ?: 115f,
                oilTempAmberC = prefs[Keys.oilAmber] ?: 120f,
                oilTempRedC = prefs[Keys.oilRed] ?: 130f,
                iatAmberC = prefs[Keys.iatAmber] ?: 50f,
                iatRedC = prefs[Keys.iatRed] ?: 60f,
                boostAmberPsi = prefs[Keys.boostAmber] ?: 18f,
                boostRedPsi = prefs[Keys.boostRed] ?: 22f,
                lowFuelPercent = prefs[Keys.lowFuel] ?: 15f,
                lowBatteryVolts = prefs[Keys.lowBatt] ?: 12f
            )
            val next = transform(current)
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

    private object WrxProfileDefaults {
        const val redline = 6700
    }
}
