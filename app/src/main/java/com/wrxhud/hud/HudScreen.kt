package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.TachVisualStyle
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot

@Composable
fun HudScreen(
    snapshot: VehicleSnapshot,
    preferences: HudPreferences,
    connectionStatus: String,
    motion: MotionReading = MotionReading(),
    speedLimit: SpeedLimitReading = SpeedLimitReading(),
    logging: Boolean = false,
    logFileName: String? = null,
    onToggleLogging: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val thresholds = preferences.toAlarmThresholds()
    val useF = preferences.tempUnit == TempUnit.F
    val useMph = preferences.speedUnit == SpeedUnit.MPH

    DangerToManifoldHost(
        enabled = preferences.dangerToManifold,
        rpm = snapshot.rpm,
        redlineRpm = preferences.redlineRpm,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HudColors.Background)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Flexible middle: tach grows/shrinks; gauges + lamps always reserved below.
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShiftLights(
                    rpm = snapshot.rpm,
                    thresholds = preferences.shiftLightStarts,
                    redline = preferences.redlineRpm,
                    pattern = preferences.shiftLightPattern,
                    visualStyle = preferences.tachVisualStyle,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = HudColors.TextDim
                    )
                }
            }

            val showTachRow = GaugeId.RPM in preferences.enabledGauges ||
                GaugeId.THROTTLE in preferences.enabledGauges ||
                GaugeId.GEAR in preferences.enabledGauges ||
                GaugeId.SPEED in preferences.enabledGauges ||
                preferences.showSpeedLimit

            if (showTachRow) {
                val speedMps = snapshot.gpsSpeedMps
                    ?: snapshot.obdSpeedKph?.let { UnitConverters.kphToMps(it) }
                // Prefer accelerator pedal %; fall back to absolute throttle plate.
                val pedalPct = snapshot.acceleratorPercent ?: snapshot.throttlePercent
                Tachometer(
                    rpm = if (GaugeId.RPM in preferences.enabledGauges) snapshot.rpm else null,
                    previousRpm = snapshot.previousRpm,
                    sampleAgeMs = snapshot.rpmSampleAgeMs,
                    redline = preferences.redlineRpm,
                    gear = if (GaugeId.GEAR in preferences.enabledGauges) snapshot.gear else null,
                    motion = motion,
                    motionDisplayMode = preferences.motionDisplayMode,
                    motionVisibility = MotionGaugeVisibility(
                        pitch = false,
                        roll = false,
                        gForce = preferences.showMotionGForce
                    ),
                    visualStyle = preferences.tachVisualStyle,
                    boostPsi = snapshot.boostPsi,
                    maxBoostPsi = preferences.boostRedPsi,
                    throttlePercent = if (GaugeId.THROTTLE in preferences.enabledGauges) pedalPct else null,
                    showThrottle = GaugeId.THROTTLE in preferences.enabledGauges,
                    speedValue = if (GaugeId.SPEED in preferences.enabledGauges) {
                        if (speedMps != null) UnitConverters.formatSpeed(speedMps, useMph) else ""
                    } else {
                        null
                    },
                    speedUnit = if (useMph) "MPH" else "KM/H",
                    speedLimit = if (preferences.showSpeedLimit) speedLimit else null,
                    showGear = GaugeId.GEAR in preferences.enabledGauges,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            Spacer(Modifier.height(2.dp))

            val helixOwnsBoost = preferences.tachVisualStyle == TachVisualStyle.HELIX

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (GaugeId.COOLANT in preferences.enabledGauges) {
                        GaugeTile(
                            label = "CLT",
                            valueText = UnitConverters.formatTemp(snapshot.coolantC, useF),
                            unit = if (useF) "°F" else "°C",
                            severity = AlarmEvaluator.severityForCoolant(snapshot.coolantC, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.OIL_TEMP in preferences.enabledGauges) {
                        GaugeTile(
                            label = "OIL°",
                            valueText = UnitConverters.formatTemp(snapshot.oilTempC, useF),
                            unit = if (useF) "°F" else "°C",
                            severity = AlarmEvaluator.severityForOilTemp(snapshot.oilTempC, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.OIL_PRESSURE in preferences.enabledGauges) {
                        GaugeTile(
                            label = "OIL#",
                            valueText = UnitConverters.formatOilPressureSwitch(snapshot.oilPressureOk),
                            unit = "SW",
                            severity = AlarmEvaluator.severityForOilPressure(snapshot.oilPressureOk),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.INTAKE_AIR_TEMP in preferences.enabledGauges) {
                        GaugeTile(
                            label = "IAT",
                            valueText = UnitConverters.formatTemp(snapshot.intakeAirTempC, useF),
                            unit = if (useF) "°F" else "°C",
                            severity = AlarmEvaluator.severityForIat(snapshot.intakeAirTempC, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.BOOST in preferences.enabledGauges && !helixOwnsBoost) {
                        BoostVacTile(
                            psi = snapshot.boostPsi,
                            unit = preferences.pressureUnit,
                            severity = AlarmEvaluator.severityForBoost(snapshot.boostPsi, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.AFR in preferences.enabledGauges) {
                        GaugeTile(
                            label = "AFR",
                            valueText = UnitConverters.formatAfr(snapshot.afr),
                            unit = ":1",
                            severity = AlarmEvaluator.severityForAfr(snapshot.afr, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.BATTERY in preferences.enabledGauges) {
                        GaugeTile(
                            label = "BATT",
                            valueText = UnitConverters.formatVoltage(snapshot.batteryVolts),
                            unit = "V",
                            severity = when {
                                snapshot.batteryVolts == null -> AlarmEvaluator.Severity.NONE
                                snapshot.batteryVolts < thresholds.lowBatteryVolts -> AlarmEvaluator.Severity.WARN
                                else -> AlarmEvaluator.Severity.NORMAL
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            WarningLamps(
                alarms = snapshot.alarms,
                enabled = preferences.enabledLamps,
                modifier = Modifier.fillMaxWidth()
            )
            } // end weight(1f) content

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = buildString {
                        append(connectionStatus)
                        if (snapshot.usingSimulator) append("  [SIM]")
                        snapshot.oilTempSource?.let { append("  OIL:$it") }
                        append("  GPS:")
                        append(preferences.gpsSourceMode.name)
                        append(if (snapshot.gpsHasFix) "+" else "-")
                        if (logging) {
                            append("  REC:")
                            append(logFileName ?: "on")
                        }
                    },
                    color = if (logging) HudColors.Red else HudColors.TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Column(horizontalAlignment = Alignment.End) {
                    DataLogRecordButton(
                        logging = logging,
                        onClick = onToggleLogging,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "WRX VB FA24  2022+",
                        color = HudColors.TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
