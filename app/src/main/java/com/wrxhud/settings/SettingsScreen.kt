package com.wrxhud.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.wrxhud.gps.GpsSourceMode
import com.wrxhud.hud.HudColors
import com.wrxhud.hud.HudThemeController
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: HudPreferences,
    onBack: () -> Unit,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit,
    onScanPids: suspend () -> List<com.wrxhud.obd.PidScanRow> = { emptyList() },
    connectionStatus: String = "",
    onRecalibrateMotion: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
        "v${info.versionName ?: "?"} (${info.longVersionCode})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HudColors.TextPrimary
                )
            }
            Text(
                text = "HUD Settings",
                color = HudColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = versionLabel,
                color = HudColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Data source")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use simulator (no car)", color = HudColors.TextPrimary, fontFamily = FontFamily.Monospace)
                Switch(
                    checked = preferences.useSimulator,
                    onCheckedChange = { checked -> onUpdate { it.copy(useSimulator = checked) } }
                )
            }

            SectionTitle("GPS source")
            Text(
                text = "Auto uses built-in GNSS when present, and falls back to Bluetooth / USB NMEA dongles (pair BT GPS in Android settings first).",
                color = HudColors.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GpsSourceMode.entries.take(2).forEach { mode ->
                    FilterChip(
                        selected = preferences.gpsSourceMode == mode,
                        onClick = { onUpdate { it.copy(gpsSourceMode = mode) } },
                        label = { Text(mode.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GpsSourceMode.entries.drop(2).forEach { mode ->
                    FilterChip(
                        selected = preferences.gpsSourceMode == mode,
                        onClick = { onUpdate { it.copy(gpsSourceMode = mode) } },
                        label = { Text(mode.label) }
                    )
                }
            }

            SectionTitle("Appearance")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pinky Mode",
                        color = if (preferences.pinkyMode) HudColors.Accent else HudColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Soft rose / hot-pink HUD theme",
                        color = HudColors.TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = preferences.pinkyMode,
                    onCheckedChange = { checked ->
                        HudThemeController.pinkyMode = checked
                        onUpdate { it.copy(pinkyMode = checked) }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "DANGER TO MANIFOLD",
                        color = if (preferences.dangerToManifold) HudColors.Amber else HudColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "If redline is hit, more than once in the current driving session, a 3 second Fast and Furious style \"Danger to manifold\" graphic will display.",
                        color = HudColors.TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = preferences.dangerToManifold,
                    onCheckedChange = { checked -> onUpdate { it.copy(dangerToManifold = checked) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "3D gauges",
                        color = if (preferences.rpmGauge3d) HudColors.Accent else HudColors.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Extruded RPM tach and shift lights with depth, bevel, and specular edge",
                        color = HudColors.TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Switch(
                    checked = preferences.rpmGauge3d,
                    onCheckedChange = { checked -> onUpdate { it.copy(rpmGauge3d = checked) } }
                )
            }

            Text(
                "Attitude display",
                color = HudColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pitch / roll / yaw: text strip, three circular car gauges, or one combined attitude ring.",
                color = HudColors.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.motionDisplayMode == mode,
                        onClick = { onUpdate { it.copy(motionDisplayMode = mode) } },
                        label = { Text(mode.label, fontFamily = FontFamily.Monospace) }
                    )
                }
            }

            Text(
                if (preferences.motionCalibrated) {
                    "Mount calibrated (P ${"%.0f".format(preferences.motionCalibPitchDeg)}° / " +
                        "R ${"%.0f".format(preferences.motionCalibRollDeg)}° / " +
                        "Y ${"%.0f".format(preferences.motionCalibYawDeg)}°)"
                } else {
                    "Mount not calibrated — attitude may be wrong until you run the test."
                },
                color = if (preferences.motionCalibrated) HudColors.Green else HudColors.Amber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onRecalibrateMotion,
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Panel)
            ) {
                Text(
                    "Recalibrate mount attitude",
                    color = HudColors.TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = { onUpdate { it.copy(onboardingComplete = false) } },
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Panel)
            ) {
                Text("Run setup wizard again", color = HudColors.TextPrimary, fontFamily = FontFamily.Monospace)
            }

            PidScanSection(
                preferences = preferences,
                connectionStatus = connectionStatus,
                onUpdate = onUpdate,
                onScanPids = onScanPids
            )

            SectionTitle("Gauges")
            GaugeId.entries.forEach { gauge ->
                ToggleRow(
                    label = gauge.name.replace('_', ' '),
                    checked = gauge in preferences.enabledGauges,
                    onCheckedChange = { enabled ->
                        onUpdate { prefs ->
                            val next = prefs.enabledGauges.toMutableSet()
                            if (enabled) next.add(gauge) else next.remove(gauge)
                            prefs.copy(enabledGauges = next)
                        }
                    }
                )
            }

            SectionTitle("Warning lamps")
            LampId.entries.forEach { lamp ->
                ToggleRow(
                    label = lamp.name.replace('_', ' '),
                    checked = lamp in preferences.enabledLamps,
                    onCheckedChange = { enabled ->
                        onUpdate { prefs ->
                            val next = prefs.enabledLamps.toMutableSet()
                            if (enabled) next.add(lamp) else next.remove(lamp)
                            prefs.copy(enabledLamps = next)
                        }
                    }
                )
            }

            SectionTitle("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferences.tempUnit == TempUnit.F,
                    onClick = { onUpdate { it.copy(tempUnit = TempUnit.F) } },
                    label = { Text("°F") }
                )
                FilterChip(
                    selected = preferences.tempUnit == TempUnit.C,
                    onClick = { onUpdate { it.copy(tempUnit = TempUnit.C) } },
                    label = { Text("°C") }
                )
                FilterChip(
                    selected = preferences.speedUnit == SpeedUnit.MPH,
                    onClick = { onUpdate { it.copy(speedUnit = SpeedUnit.MPH) } },
                    label = { Text("MPH") }
                )
                FilterChip(
                    selected = preferences.speedUnit == SpeedUnit.KPH,
                    onClick = { onUpdate { it.copy(speedUnit = SpeedUnit.KPH) } },
                    label = { Text("KM/H") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = preferences.pressureUnit == unit,
                        onClick = { onUpdate { it.copy(pressureUnit = unit) } },
                        label = { Text(unit.name) }
                    )
                }
            }

            SectionTitle("Shift lights / redline")
            Text(
                text = "Fill pattern",
                color = HudColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftLightPattern.entries.take(2).forEach { pattern ->
                    FilterChip(
                        selected = preferences.shiftLightPattern == pattern,
                        onClick = { onUpdate { it.copy(shiftLightPattern = pattern) } },
                        label = { Text(pattern.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftLightPattern.entries.drop(2).forEach { pattern ->
                    FilterChip(
                        selected = preferences.shiftLightPattern == pattern,
                        onClick = { onUpdate { it.copy(shiftLightPattern = pattern) } },
                        label = { Text(pattern.label) }
                    )
                }
            }
            IntField(
                label = "Redline RPM",
                value = preferences.redlineRpm,
                onCommit = { v -> onUpdate { it.copy(redlineRpm = v) } }
            )
            preferences.shiftLightStarts.forEachIndexed { index, rpm ->
                IntField(
                    label = "Shift light ${index + 1}",
                    value = rpm,
                    onCommit = { v ->
                        onUpdate { prefs ->
                            val list = prefs.shiftLightStarts.toMutableList()
                            list[index] = v
                            prefs.copy(shiftLightStarts = list)
                        }
                    }
                )
            }

            SectionTitle("Transmission / tires")
            ToggleRow(
                label = "SPT / CVT (else 6MT)",
                checked = preferences.useSptCvt,
                onCheckedChange = { checked -> onUpdate { it.copy(useSptCvt = checked) } }
            )
            IntField("Tire width mm", preferences.tireWidthMm) { v -> onUpdate { it.copy(tireWidthMm = v) } }
            IntField("Tire aspect", preferences.tireAspect) { v -> onUpdate { it.copy(tireAspect = v) } }
            IntField("Wheel diameter in", preferences.wheelDiameterIn) { v -> onUpdate { it.copy(wheelDiameterIn = v) } }

            SectionTitle("Alarm thresholds")
            FloatField("Coolant amber °C", preferences.coolantAmberC) { v -> onUpdate { it.copy(coolantAmberC = v) } }
            FloatField("Coolant red °C", preferences.coolantRedC) { v -> onUpdate { it.copy(coolantRedC = v) } }
            FloatField("Oil temp amber °C", preferences.oilTempAmberC) { v -> onUpdate { it.copy(oilTempAmberC = v) } }
            FloatField("Oil temp red °C", preferences.oilTempRedC) { v -> onUpdate { it.copy(oilTempRedC = v) } }
            FloatField("IAT amber °C", preferences.iatAmberC) { v -> onUpdate { it.copy(iatAmberC = v) } }
            FloatField("IAT red °C", preferences.iatRedC) { v -> onUpdate { it.copy(iatRedC = v) } }
            FloatField("Boost amber psi", preferences.boostAmberPsi) { v -> onUpdate { it.copy(boostAmberPsi = v) } }
            FloatField("Boost red psi", preferences.boostRedPsi) { v -> onUpdate { it.copy(boostRedPsi = v) } }
            FloatField("Low fuel %", preferences.lowFuelPercent) { v -> onUpdate { it.copy(lowFuelPercent = v) } }
            FloatField("Low battery V", preferences.lowBatteryVolts) { v -> onUpdate { it.copy(lowBatteryVolts = v) } }
            FloatField("Oil pressure amber psi", preferences.oilPressureAmberPsi) { v ->
                onUpdate { it.copy(oilPressureAmberPsi = v) }
            }
            FloatField("Oil pressure red psi", preferences.oilPressureRedPsi) { v ->
                onUpdate { it.copy(oilPressureRedPsi = v) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = HudColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = HudColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IntField(label: String, value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = outlinedColors()
    )
}

@Composable
private fun FloatField(label: String, value: Float, onCommit: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = outlinedColors()
    )
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HudColors.TextPrimary,
    unfocusedTextColor = HudColors.TextPrimary,
    focusedBorderColor = HudColors.Accent,
    unfocusedBorderColor = HudColors.Border,
    focusedLabelColor = HudColors.TextDim,
    unfocusedLabelColor = HudColors.TextDim,
    cursorColor = HudColors.Accent
)
