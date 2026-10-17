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
import com.wrxhud.gps.GpsSourceMode
import com.wrxhud.obd.PidAssignment
import com.wrxhud.obd.PidScanLogEntry
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
        val shiftLightPattern = stringPreferencesKey("shift_light_pattern")
        val shiftLights = stringPreferencesKey("shift_lights")
        val useSpt = booleanPreferencesKey("use_spt")
        val tireWidth = intPreferencesKey("tire_width")
        val tireAspect = intPreferencesKey("tire_aspect")
        val wheelIn = intPreferencesKey("wheel_in")
        val useSim = booleanPreferencesKey("use_sim")
        val pinkyMode = booleanPreferencesKey("pinky_mode")
        val colorTheme = stringPreferencesKey("color_theme")
        val dangerToManifold = booleanPreferencesKey("danger_to_manifold")
        val rpmGauge3d = booleanPreferencesKey("rpm_gauge_3d")
        val tachVisualStyle = stringPreferencesKey("tach_visual_style")
        val motionDisplayMode = stringPreferencesKey("motion_display_mode")
        val showMotionPitch = booleanPreferencesKey("show_motion_pitch")
        val showMotionRoll = booleanPreferencesKey("show_motion_roll")
        val showMotionGForce = booleanPreferencesKey("show_motion_gforce")
        val motionCalibrated = booleanPreferencesKey("motion_calibrated")
        val motionCalibPitch = floatPreferencesKey("motion_calib_pitch")
        val motionCalibRoll = floatPreferencesKey("motion_calib_roll")
        val motionCalibYaw = floatPreferencesKey("motion_calib_yaw")
        val motionCalibG = floatPreferencesKey("motion_calib_g")
        val gpsSourceMode = stringPreferencesKey("gps_source_mode")
        val bluetoothGpsAddress = stringPreferencesKey("bluetooth_gps_address")
        val showSpeedLimit = booleanPreferencesKey("show_speed_limit")
        val googleMapsApiKey = stringPreferencesKey("google_maps_api_key")
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
        val oilPressureAmber = floatPreferencesKey("oil_pressure_amber")
        val oilPressureRed = floatPreferencesKey("oil_pressure_red")
        val pidAssignments = stringPreferencesKey("pid_assignments")
        val pidScanLog = stringPreferencesKey("pid_scan_log")
        val pidScanLogAt = stringPreferencesKey("pid_scan_log_at")
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
            prefs[Keys.shiftLightPattern] = next.shiftLightPattern.name
            prefs[Keys.shiftLights] = next.shiftLightStarts.joinToString(",")
            prefs[Keys.useSpt] = next.useSptCvt
            prefs[Keys.tireWidth] = next.tireWidthMm
            prefs[Keys.tireAspect] = next.tireAspect
            prefs[Keys.wheelIn] = next.wheelDiameterIn
            prefs[Keys.useSim] = next.useSimulator
            prefs[Keys.pinkyMode] = next.colorTheme == HudColorTheme.PINKY
            prefs[Keys.colorTheme] = next.colorTheme.name
            prefs[Keys.dangerToManifold] = next.dangerToManifold
            prefs[Keys.rpmGauge3d] = next.tachVisualStyle == TachVisualStyle.BAR_3D
            prefs[Keys.tachVisualStyle] = next.tachVisualStyle.name
            prefs[Keys.motionDisplayMode] = next.motionDisplayMode.name
            prefs[Keys.showMotionPitch] = next.showMotionPitch
            prefs[Keys.showMotionRoll] = next.showMotionRoll
            prefs[Keys.showMotionGForce] = next.showMotionGForce
            prefs[Keys.motionCalibrated] = next.motionCalibrated
            prefs[Keys.motionCalibPitch] = next.motionCalibPitchDeg
            prefs[Keys.motionCalibRoll] = next.motionCalibRollDeg
            prefs[Keys.motionCalibYaw] = next.motionCalibYawDeg
            prefs[Keys.motionCalibG] = next.motionCalibGForce
            prefs[Keys.gpsSourceMode] = next.gpsSourceMode.name
            prefs[Keys.bluetoothGpsAddress] = next.bluetoothGpsAddress
            prefs[Keys.showSpeedLimit] = next.showSpeedLimit
            // Do not persist Maps API keys (unused; exclude from backup surface).
            prefs[Keys.googleMapsApiKey] = ""
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
            prefs[Keys.oilPressureAmber] = next.oilPressureAmberPsi
            prefs[Keys.oilPressureRed] = next.oilPressureRedPsi
            prefs[Keys.pidAssignments] = next.pidAssignments.joinToString(";") { it.encode() }
            prefs[Keys.pidScanLog] = next.pidScanLog.joinToString(";") { it.encode() }
            prefs[Keys.pidScanLogAt] = next.pidScanLogAtMs.toString()
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
                ?.filter { it != LampId.KNOCK && it != LampId.DAM }
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
            shiftLightPattern = this[Keys.shiftLightPattern]
                ?.let { runCatching { ShiftLightPattern.valueOf(it) }.getOrNull() }
                ?: defaults.shiftLightPattern,
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
            pinkyMode = this[Keys.pinkyMode] ?: defaults.pinkyMode,
            colorTheme = HudColorTheme.fromStored(
                this[Keys.colorTheme],
                pinkyFallback = this[Keys.pinkyMode] ?: false
            ),
            dangerToManifold = this[Keys.dangerToManifold] ?: defaults.dangerToManifold,
            rpmGauge3d = this[Keys.rpmGauge3d] ?: defaults.rpmGauge3d,
            tachVisualStyle = TachVisualStyle.fromStored(
                this[Keys.tachVisualStyle],
                rpm3dFallback = this[Keys.rpmGauge3d] ?: true
            ),
            motionDisplayMode = MotionDisplayMode.fromStored(this[Keys.motionDisplayMode])
                ?: defaults.motionDisplayMode,
            showMotionPitch = this[Keys.showMotionPitch] ?: defaults.showMotionPitch,
            showMotionRoll = this[Keys.showMotionRoll] ?: defaults.showMotionRoll,
            showMotionGForce = this[Keys.showMotionGForce] ?: defaults.showMotionGForce,
            motionCalibrated = this[Keys.motionCalibrated] ?: defaults.motionCalibrated,
            motionCalibPitchDeg = this[Keys.motionCalibPitch] ?: defaults.motionCalibPitchDeg,
            motionCalibRollDeg = this[Keys.motionCalibRoll] ?: defaults.motionCalibRollDeg,
            motionCalibYawDeg = this[Keys.motionCalibYaw] ?: defaults.motionCalibYawDeg,
            motionCalibGForce = this[Keys.motionCalibG] ?: defaults.motionCalibGForce,
            gpsSourceMode = this[Keys.gpsSourceMode]
                ?.let { runCatching { GpsSourceMode.valueOf(it) }.getOrNull() }
                ?: defaults.gpsSourceMode,
            bluetoothGpsAddress = this[Keys.bluetoothGpsAddress] ?: defaults.bluetoothGpsAddress,
            showSpeedLimit = this[Keys.showSpeedLimit] ?: defaults.showSpeedLimit,
            googleMapsApiKey = this[Keys.googleMapsApiKey] ?: defaults.googleMapsApiKey,
            coolantAmberC = this[Keys.coolantAmber] ?: defaults.coolantAmberC,
            coolantRedC = this[Keys.coolantRed] ?: defaults.coolantRedC,
            oilTempAmberC = this[Keys.oilAmber] ?: defaults.oilTempAmberC,
            oilTempRedC = this[Keys.oilRed] ?: defaults.oilTempRedC,
            iatAmberC = this[Keys.iatAmber] ?: defaults.iatAmberC,
            iatRedC = this[Keys.iatRed] ?: defaults.iatRedC,
            boostAmberPsi = this[Keys.boostAmber] ?: defaults.boostAmberPsi,
            boostRedPsi = this[Keys.boostRed] ?: defaults.boostRedPsi,
            lowFuelPercent = this[Keys.lowFuel] ?: defaults.lowFuelPercent,
            lowBatteryVolts = this[Keys.lowBatt] ?: defaults.lowBatteryVolts,
            oilPressureAmberPsi = this[Keys.oilPressureAmber] ?: defaults.oilPressureAmberPsi,
            oilPressureRedPsi = this[Keys.oilPressureRed] ?: defaults.oilPressureRedPsi,
            pidAssignments = this[Keys.pidAssignments]
                ?.split(";")
                ?.mapNotNull { PidAssignment.decode(it.trim()) }
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.pidAssignments,
            pidScanLog = this[Keys.pidScanLog]
                ?.split(";")
                ?.mapNotNull { PidScanLogEntry.decode(it.trim()) }
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.pidScanLog,
            pidScanLogAtMs = this[Keys.pidScanLogAt]?.toLongOrNull()
                ?: defaults.pidScanLogAtMs
        )
    }
}
