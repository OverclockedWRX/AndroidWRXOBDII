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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.hud.HudColors
import com.wrxhud.settings.HudPreferences
import com.wrxhud.telemetry.AppPage
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.LampId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit

private const val PAGE_COUNT = 7

@Composable
fun OnboardingWizard(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit,
    onRequestPermissions: () -> Unit,
    onFinished: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .padding(20.dp)
    ) {
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
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
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
                .padding(vertical = 16.dp)
        ) {
            when (page) {
                0 -> WelcomePage()
                1 -> DataSourcePage(preferences, onUpdate)
                2 -> GaugesPage(preferences, onUpdate)
                3 -> LampsPage(preferences, onUpdate)
                4 -> UnitsPage(preferences, onUpdate)
                5 -> DefaultPageChooser(preferences, onUpdate)
                6 -> PermissionsPage()
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
                        onRequestPermissions()
                        onUpdate { it.copy(onboardingComplete = true) }
                        onFinished()
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
            "You will choose gauges, warning lamps, units, and the default home tab.\n\n" +
            "Android permissions (Bluetooth, location, notifications) are requested on the last step only.",
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
        text = "Use the simulator to preview the HUD without a car. Turn it off when your OBDLink MX+ is paired.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(16.dp))
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
private fun DefaultPageChooser(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    PageTitle("Default tab")
    Text(
        text = "HUD is recommended as the default. You can switch tabs anytime.",
        color = HudColors.TextDim,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = preferences.defaultPage == AppPage.HUD,
            onClick = { onUpdate { it.copy(defaultPage = AppPage.HUD) } },
            label = { Text("HUD") }
        )
        FilterChip(
            selected = preferences.defaultPage == AppPage.NAVIGATION,
            onClick = { onUpdate { it.copy(defaultPage = AppPage.NAVIGATION) } },
            label = { Text("Navigation") }
        )
    }
}

@Composable
private fun PermissionsPage() {
    PageTitle("Permissions")
    Text(
        text = "The next step asks Android for:\n\n" +
            "• Nearby devices / Bluetooth — talk to the OBDLink MX+\n" +
            "• Location — GPS speed and Google Maps navigation\n" +
            "• Notifications — keep the OBD service alive\n\n" +
            "Tap “Grant access & start” to continue.",
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
