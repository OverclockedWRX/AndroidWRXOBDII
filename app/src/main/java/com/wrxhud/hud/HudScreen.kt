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
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShiftLights(
                    rpm = snapshot.rpm,
                    thresholds = preferences.shiftLightStarts,
                    redline = preferences.redlineRpm,
                    pattern = preferences.shiftLightPattern,
                    threeDimensional = preferences.rpmGauge3d,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = HudColors.TextDim
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (GaugeId.RPM in preferences.enabledGauges || GaugeId.GEAR in preferences.enabledGauges) {
                Tachometer(
                    rpm = if (GaugeId.RPM in preferences.enabledGauges) snapshot.rpm else null,
                    previousRpm = snapshot.previousRpm,
                    sampleAgeMs = snapshot.rpmSampleAgeMs,
                    redline = preferences.redlineRpm,
                    gear = if (GaugeId.GEAR in preferences.enabledGauges) snapshot.gear else null,
                    motion = motion,
                    motionDisplayMode = preferences.motionDisplayMode,
                    threeDimensional = preferences.rpmGauge3d
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (GaugeId.SPEED in preferences.enabledGauges) {
                    SpeedTile(
                        label = "SPEED",
                        value = UnitConverters.formatSpeed(snapshot.gpsSpeedMps, useMph),
                        unit = if (useMph) "MPH GPS" else "KM/H GPS"
                    )
                }
                if (preferences.showSpeedLimit) {
                    SpeedLimitSign(reading = speedLimit)
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (GaugeId.COOLANT in preferences.enabledGauges) {
                        GaugeTile(
                            label = "COOLANT",
                            valueText = UnitConverters.formatTemp(snapshot.coolantC, useF),
                            unit = if (useF) "°F" else "°C",
                            severity = AlarmEvaluator.severityForCoolant(snapshot.coolantC, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.OIL_TEMP in preferences.enabledGauges) {
                        GaugeTile(
                            label = "OIL TEMP",
                            valueText = UnitConverters.formatTemp(snapshot.oilTempC, useF),
                            unit = if (useF) "°F" else "°C",
                            severity = AlarmEvaluator.severityForOilTemp(snapshot.oilTempC, thresholds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (GaugeId.OIL_PRESSURE in preferences.enabledGauges) {
                        GaugeTile(
                            label = "OIL PSI",
                            valueText = UnitConverters.formatPressure(
                                snapshot.oilPressurePsi,
                                preferences.pressureUnit
                            ),
                            unit = preferences.pressureUnit.name,
                            severity = AlarmEvaluator.severityForOilPressure(
                                snapshot.oilPressurePsi,
                                thresholds
                            ),
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
                    if (GaugeId.BOOST in preferences.enabledGauges) {
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

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
                    },
                    color = HudColors.TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "WRX VB FA24  2022+",
                    color = HudColors.TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
