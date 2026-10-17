package com.wrxhud.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.wrxhud.telemetry.TelemetryHub
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.UnitConverters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Android Auto instrument display: key gauges only.
 */
class HudCarScreen(carContext: CarContext) : Screen(carContext) {
    private var collectJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                collectJob = lifecycleScope.launch {
                    combine(TelemetryHub.snapshot, TelemetryHub.preferences) { snap, prefs ->
                        snap to prefs
                    }.collect { invalidate() }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                collectJob?.cancel()
                collectJob = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        val snap = TelemetryHub.snapshot.value
        val prefs = TelemetryHub.preferences.value
        val useF = prefs.tempUnit == TempUnit.F
        val useMph = prefs.speedUnit == SpeedUnit.MPH

        val paneBuilder = Pane.Builder()

        fun addRow(title: String, value: String) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(title)
                    .addText(value)
                    .build()
            )
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

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("WRX Race HUD")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
