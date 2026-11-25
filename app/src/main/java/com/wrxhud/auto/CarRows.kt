package com.wrxhud.auto

import com.wrxhud.settings.HudPreferences
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

data class CarRow(val title: String, val text: String)

/** Pure builder for the Android Auto pane rows (what is actually displayed). */
object CarRows {
    fun build(snap: VehicleSnapshot, prefs: HudPreferences): List<CarRow> {
        val useF = prefs.tempUnit == TempUnit.F
        val useMph = prefs.speedUnit == SpeedUnit.MPH
        val rows = mutableListOf<CarRow>()
        fun addRow(title: String, value: String) {
            rows += CarRow(title, value)
        }
        if (GaugeId.RPM in prefs.enabledGauges) {
            addRow("RPM", UnitConverters.formatRpm(snap.rpm))
        }
        if (GaugeId.THROTTLE in prefs.enabledGauges) {
            val pedal = snap.acceleratorPercent ?: snap.throttlePercent
            addRow("Throttle", UnitConverters.formatPercent(pedal))
        }
        if (GaugeId.GEAR in prefs.enabledGauges) {
            addRow("GEAR", UnitConverters.formatGear(snap.gear))
        }
        if (GaugeId.SPEED in prefs.enabledGauges) {
            val speedMps = snap.gpsSpeedMps
                ?: snap.obdSpeedKph?.let { UnitConverters.kphToMps(it) }
            if (speedMps != null) {
                val speed = UnitConverters.formatSpeed(speedMps, useMph)
                addRow("SPD", "$speed ${if (useMph) "MPH" else "KM/H"}")
            }
        }
        if (GaugeId.BOOST in prefs.enabledGauges) {
            val boostLabel = when {
                snap.boostPsi == null -> "BST"
                snap.boostPsi > 0f -> "BST"
                else -> "VAC"
            }
            addRow(
                boostLabel,
                "${UnitConverters.formatPressure(snap.boostPsi, prefs.pressureUnit)} ${prefs.pressureUnit.name}"
            )
        }
        if (GaugeId.COOLANT in prefs.enabledGauges) {
            addRow(
                "CLT",
                "${UnitConverters.formatTemp(snap.coolantC, useF)}${if (useF) "°F" else "°C"}"
            )
        }
        if (GaugeId.OIL_TEMP in prefs.enabledGauges) {
            addRow(
                "OIL°",
                "${UnitConverters.formatTemp(snap.oilTempC, useF)}${if (useF) "°F" else "°C"}"
            )
        }
        if (GaugeId.OIL_PRESSURE in prefs.enabledGauges) {
            addRow("OIL#", UnitConverters.formatOilPressureSwitch(snap.oilPressureOk))
        }
        if (GaugeId.INTAKE_AIR_TEMP in prefs.enabledGauges) {
            addRow(
                "IAT",
                "${UnitConverters.formatTemp(snap.intakeAirTempC, useF)}${if (useF) "°F" else "°C"}"
            )
        }
        if (GaugeId.AFR in prefs.enabledGauges) {
            addRow("AFR", "${UnitConverters.formatAfr(snap.afr)} :1")
        }
        if (GaugeId.BATTERY in prefs.enabledGauges) {
            addRow("BATT", "${UnitConverters.formatVoltage(snap.batteryVolts)} V")
        }

        val status = buildString {
            append(if (snap.usingSimulator) "SIM" else if (snap.obdConnected) "OBD OK" else "OBD…")
            if (snap.alarms.cel) append("  CEL")
            if (snap.alarms.highCoolant) append("  HOT")
            if (snap.alarms.overboost) append("  BST")
        }
        addRow("STATUS", status)

        return rows
    }
}

/**
 * Android Auto refresh stream: only changes of the displayed rows count, and at most one
 * emission per [periodMs] (the Car App host rejects rapid invalidate() calls).
 */
@OptIn(FlowPreview::class)
fun Flow<List<CarRow>>.carRefresh(periodMs: Long = 400L): Flow<List<CarRow>> =
    distinctUntilChanged().sample(periodMs)
