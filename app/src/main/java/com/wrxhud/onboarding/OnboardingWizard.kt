package com.wrxhud.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.gps.GpsSourceMode
import com.wrxhud.hud.HudColors
import com.wrxhud.hud.HudThemeController
import com.wrxhud.obd.PidScanRow
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.MotionDisplayMode
import com.wrxhud.settings.PidScanSection
import com.wrxhud.settings.ShiftLightPattern
import com.wrxhud.settings.SpeedLimitRequirementWarning
import com.wrxhud.settings.TachVisualStyle
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit

private const val PAGE_COUNT = 12

@Composable
fun OnboardingWizard(
    preferences: HudPreferences,
    connectionStatus: String,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit,
    onEnsureObd: (useSimulator: Boolean) -> Unit,
    onScanPids: suspend () -> List<PidScanRow>,
    onRequestPermissions: () -> Unit,
    onFinished: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    fun finishWizard() {
        onRequestPermissions()
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WRX Race HUD",
                    color = HudColors.Accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Setup  ${page + 1} / $PAGE_COUNT",
                    color = HudColors.TextDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            TextButton(onClick = { finishWizard() }) {
                Text(
                    "Skip",
                    color = HudColors.Amber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LinearProgressIndicator(
            progress = { (page + 1).toFloat() / PAGE_COUNT },
            modifier = Modifier.fillMaxWidth(),
            color = HudColors.Accent,
            trackColor = HudColors.Border
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (page) {
                0 -> WelcomePage()
                1 -> DataSourcePage(preferences, onUpdate)
                2 -> AppearancePage(preferences, onUpdate)
                3 -> AttitudePage(preferences, onUpdate)
                4 -> GaugesPage(preferences, onUpdate)
                5 -> LampsPage(preferences, onUpdate)
                6 -> UnitsPage(preferences, onUpdate)
                7 -> ShiftPage(preferences, onUpdate)
                8 -> TiresPage(preferences, onUpdate)
                9 -> AlarmsPage(preferences, onUpdate)
                10 -> PidMappingPage(
                    preferences = preferences,
                    connectionStatus = connectionStatus,
                    onUpdate = onUpdate,
                    onEnsureObd = onEnsureObd,
                    onScanPids = onScanPids
                )
                11 -> PermissionsPage()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { if (page > 0) page-- },
                enabled = page > 0
            ) {
                Text("Back")
            }
            Button(
                onClick = {
                    if (page < PAGE_COUNT - 1) {
                        page++
                    } else {
                        // Permissions first; onboardingComplete is set after the dialog returns
                        // so OBD/GPS do not start without grants (avoids SecurityException crash).
                        finishWizard()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent)
            ) {
                Text(if (page < PAGE_COUNT - 1) "Next" else "Grant access & start")
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Text(
        text = "Welcome",
        color = HudColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "This wizard configures your race HUD for a 2022+ Subaru WRX with an OBDLink MX+.\n\n" +
            "You will set data source, look, gauges, lamps, units, shift lights, tires, alarms, " +
            "and optionally map scanned PIDs to gauges.\n\n" +
            "Tap Skip anytime to use defaults and jump to permissions. Everything can be changed later in Settings.",
        color = HudColors.TextPrimary,
        fontSize = 15.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 22.sp
    )
}

@Composable
private fun DataSourcePage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Data source")
    Text(
        text = "Use the simulator to preview without a car. Turn it off when your OBDLink MX+ is paired.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Simulator mode", color = HudColors.TextPrimary, fontFamily = FontFamily.Monospace)
        Switch(
            checked = preferences.useSimulator,
            onCheckedChange = { checked -> onUpdate { it.copy(useSimulator = checked) } }
        )
    }

    Spacer(Modifier.height(16.dp))
    PageSubtitle("GPS source")
    Text(
        text = "Auto uses built-in GNSS when present, with Bluetooth / USB NMEA fallback.",
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
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

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Show speed limit", color = HudColors.TextPrimary, fontFamily = FontFamily.Monospace)
            Text(
                "Posted limit from OpenStreetMap.",
                color = HudColors.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = preferences.showSpeedLimit,
            onCheckedChange = { checked -> onUpdate { it.copy(showSpeedLimit = checked) } }
        )
    }
    Spacer(Modifier.height(8.dp))
    SpeedLimitRequirementWarning()
}

@Composable
private fun AppearancePage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Appearance")
    PageSubtitle("Color theme")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HudColorTheme.entries.take(3).forEach { theme ->
            FilterChip(
                selected = preferences.colorTheme == theme,
                onClick = {
                    HudThemeController.colorTheme = theme
                    onUpdate { it.copy(colorTheme = theme, pinkyMode = theme == HudColorTheme.PINKY) }
                },
                label = { Text(theme.label, fontFamily = FontFamily.Monospace) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HudColorTheme.entries.drop(3).forEach { theme ->
            FilterChip(
                selected = preferences.colorTheme == theme,
                onClick = {
                    HudThemeController.colorTheme = theme
                    onUpdate { it.copy(colorTheme = theme, pinkyMode = theme == HudColorTheme.PINKY) }
                },
                label = { Text(theme.label, fontFamily = FontFamily.Monospace) }
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    PageSubtitle("Tach / shift style")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TachVisualStyle.entries.take(2).forEach { style ->
            FilterChip(
                selected = preferences.tachVisualStyle == style,
                onClick = {
                    onUpdate {
                        it.copy(
                            tachVisualStyle = style,
                            rpmGauge3d = style == TachVisualStyle.BAR_3D
                        )
                    }
                },
                label = { Text(style.label, fontFamily = FontFamily.Monospace) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TachVisualStyle.entries.drop(2).forEach { style ->
            FilterChip(
                selected = preferences.tachVisualStyle == style,
                onClick = {
                    onUpdate {
                        it.copy(
                            tachVisualStyle = style,
                            rpmGauge3d = style == TachVisualStyle.BAR_3D
                        )
                    }
                },
                label = { Text(style.label, fontFamily = FontFamily.Monospace) }
            )
        }
    }

    Spacer(Modifier.height(12.dp))
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
                "Easter-egg overlay after the 2nd+ redline hit this session.",
                color = HudColors.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = preferences.dangerToManifold,
            onCheckedChange = { checked -> onUpdate { it.copy(dangerToManifold = checked) } }
        )
    }
}

@Composable
private fun AttitudePage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Attitude gauges")
    Text(
        text = "Pitch / roll / G next to the tach. Mount calibration runs after this wizard.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
    PageSubtitle("Layout")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MotionDisplayMode.entries.forEach { mode ->
            FilterChip(
                selected = preferences.motionDisplayMode == mode,
                onClick = { onUpdate { it.copy(motionDisplayMode = mode) } },
                label = { Text(mode.label) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ToggleRow(
        label = "Show pitch",
        checked = preferences.showMotionPitch,
        onCheckedChange = { checked -> onUpdate { it.copy(showMotionPitch = checked) } }
    )
    ToggleRow(
        label = "Show roll",
        checked = preferences.showMotionRoll,
        onCheckedChange = { checked -> onUpdate { it.copy(showMotionRoll = checked) } }
    )
    ToggleRow(
        label = "Show G-force",
        checked = preferences.showMotionGForce,
        onCheckedChange = { checked -> onUpdate { it.copy(showMotionGForce = checked) } }
    )
}

@Composable
private fun GaugesPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("HUD gauges")
    Text(
        text = "Select what appears on the race HUD. You can change this later in Settings.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
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
}

@Composable
private fun LampsPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Warning lamps")
    GaugeIdNote("Choose which warning indicators to show.")
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
}

@Composable
private fun UnitsPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Units")
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
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PressureUnit.entries.forEach { unit ->
            FilterChip(
                selected = preferences.pressureUnit == unit,
                onClick = { onUpdate { it.copy(pressureUnit = unit) } },
                label = { Text(unit.name) }
            )
        }
    }
}

@Composable
private fun ShiftPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Shift lights")
    PageSubtitle("Fill pattern")
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
    Spacer(Modifier.height(8.dp))
    IntField(
        label = "Redline RPM",
        value = preferences.redlineRpm,
        onCommit = { v -> onUpdate { it.copy(redlineRpm = v) } }
    )
    Text(
        text = "Per-light RPM starts can be fine-tuned later in Settings → Shift.",
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun TiresPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Transmission / tires")
    Text(
        text = "Used for gear estimation from RPM and speed.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
    ToggleRow(
        label = "SPT / CVT (else 6MT)",
        checked = preferences.useSptCvt,
        onCheckedChange = { checked -> onUpdate { it.copy(useSptCvt = checked) } }
    )
    IntField("Tire width mm", preferences.tireWidthMm) { v -> onUpdate { it.copy(tireWidthMm = v) } }
    IntField("Tire aspect", preferences.tireAspect) { v -> onUpdate { it.copy(tireAspect = v) } }
    IntField("Wheel diameter in", preferences.wheelDiameterIn) { v ->
        onUpdate { it.copy(wheelDiameterIn = v) }
    }
}

@Composable
private fun AlarmsPage(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Alarm thresholds")
    Text(
        text = "Amber / red trip points for warning lamps.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
    FloatField("Coolant amber °C", preferences.coolantAmberC) { v ->
        onUpdate { it.copy(coolantAmberC = v) }
    }
    FloatField("Coolant red °C", preferences.coolantRedC) { v -> onUpdate { it.copy(coolantRedC = v) } }
    FloatField("Oil temp amber °C", preferences.oilTempAmberC) { v ->
        onUpdate { it.copy(oilTempAmberC = v) }
    }
    FloatField("Oil temp red °C", preferences.oilTempRedC) { v -> onUpdate { it.copy(oilTempRedC = v) } }
    FloatField("IAT amber °C", preferences.iatAmberC) { v -> onUpdate { it.copy(iatAmberC = v) } }
    FloatField("IAT red °C", preferences.iatRedC) { v -> onUpdate { it.copy(iatRedC = v) } }
    FloatField("Boost amber psi", preferences.boostAmberPsi) { v ->
        onUpdate { it.copy(boostAmberPsi = v) }
    }
    FloatField("Boost red psi", preferences.boostRedPsi) { v -> onUpdate { it.copy(boostRedPsi = v) } }
    FloatField("Low fuel %", preferences.lowFuelPercent) { v ->
        onUpdate { it.copy(lowFuelPercent = v) }
    }
    FloatField("Low battery V", preferences.lowBatteryVolts) { v ->
        onUpdate { it.copy(lowBatteryVolts = v) }
    }
    FloatField("Oil pressure amber psi", preferences.oilPressureAmberPsi) { v ->
        onUpdate { it.copy(oilPressureAmberPsi = v) }
    }
    FloatField("Oil pressure red psi", preferences.oilPressureRedPsi) { v ->
        onUpdate { it.copy(oilPressureRedPsi = v) }
    }
}

@Composable
private fun PidMappingPage(
    preferences: HudPreferences,
    connectionStatus: String,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit,
    onEnsureObd: (useSimulator: Boolean) -> Unit,
    onScanPids: suspend () -> List<PidScanRow>
) {
    PageTitle("PID scan & mapping")
    Text(
        text = "If your OBDLink MX+ is already connected, we scan Mode 01 PIDs and known Mode 22 DIDs, " +
            "then you can assign them to gauges (oil temp, AFR, etc.).",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Link: $connectionStatus",
        color = HudColors.TextPrimary,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )

    LaunchedEffect(preferences.useSimulator) {
        if (!preferences.useSimulator) {
            onEnsureObd(false)
        }
    }

    if (preferences.useSimulator) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Simulator is on — turn it off on the Data source step to scan a real MX+.",
            color = HudColors.Amber,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    } else if (!connectionStatus.contains("Connected", ignoreCase = true)) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Waiting for OBDLink… Pair the MX+ in Android Bluetooth settings, then tap Connect. " +
                "Bluetooth permission is requested on the last step if not already granted.",
            color = HudColors.Amber,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onEnsureObd(false) },
            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent)
        ) {
            Text("Connect OBDLink", fontFamily = FontFamily.Monospace)
        }
    }

    Spacer(Modifier.height(12.dp))
    PidScanSection(
        preferences = preferences,
        connectionStatus = connectionStatus,
        onUpdate = onUpdate,
        onScanPids = onScanPids,
        autoScanWhenConnected = !preferences.useSimulator
    )
}

@Composable
private fun PermissionsPage() {
    PageTitle("Permissions")
    Text(
        text = "The next step asks Android for:\n\n" +
            "• Nearby devices / Bluetooth — talk to the OBDLink MX+\n" +
            "• Location — GPS speed (built-in, Bluetooth, or USB dongle)\n" +
            "• Notifications — keep the OBD service alive\n\n" +
            "Tap “Grant access & start” to continue. Mount attitude calibration follows.",
        color = HudColors.TextPrimary,
        fontSize = 15.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 22.sp
    )
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        color = HudColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PageSubtitle(text: String) {
    Text(
        text = text,
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun GaugeIdNote(text: String) {
    Text(
        text = text,
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(8.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = fieldColors()
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HudColors.TextPrimary,
    unfocusedTextColor = HudColors.TextPrimary,
    focusedBorderColor = HudColors.Accent,
    unfocusedBorderColor = HudColors.Border,
    focusedLabelColor = HudColors.TextDim,
    unfocusedLabelColor = HudColors.TextDim,
    cursorColor = HudColors.Accent
)
