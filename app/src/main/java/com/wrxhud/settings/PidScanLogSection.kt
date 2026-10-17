package com.wrxhud.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.hud.HudColors
import com.wrxhud.obd.MappableSignal
import com.wrxhud.obd.PidAssignment
import com.wrxhud.obd.PidDecode
import com.wrxhud.obd.PidScanLogEntry
import com.wrxhud.vehicle.GaugeId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PidScanLogSection(
    preferences: HudPreferences,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit
) {
    val stamp = remember(preferences.pidScanLogAtMs) {
        if (preferences.pidScanLogAtMs <= 0L) null
        else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(preferences.pidScanLogAtMs))
    }

    Text(
        text = "PID LOG",
        color = HudColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        text = "Every successful scan merges responding PIDs/DIDs here so you can figure out " +
            "what they are later. A text dump is also written to Downloads. Assign from this list " +
            "the same way as on the PIDs tab.",
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp
    )

    if (preferences.pidScanLog.isEmpty()) {
        Text(
            text = "No scan log yet — run Scan PIDs on the PIDs tab (or during setup).",
            color = HudColors.Amber,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    Text(
        text = "${preferences.pidScanLog.size} logged · last scan ${stamp ?: "—"}",
        color = HudColors.TextPrimary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 6.dp)
    )
    TextButton(
        onClick = {
            onUpdate { it.copy(pidScanLog = emptyList(), pidScanLogAtMs = 0L) }
        }
    ) {
        Text("Clear PID log", fontFamily = FontFamily.Monospace, color = HudColors.Accent)
    }

    preferences.pidScanLog.forEach { entry ->
        PidLogEntryCard(
            entry = entry,
            preferences = preferences,
            onAssign = { signal, decode ->
                onUpdate { prefs ->
                    val without = prefs.pidAssignments.filterNot {
                        it.signal == signal || (it.mode == entry.mode && it.id == entry.id)
                    }
                    val next = without + PidAssignment(
                        signal = signal,
                        mode = entry.mode,
                        id = entry.id,
                        decode = decode,
                        header = entry.header
                    )
                    val gauges = prefs.enabledGauges.toMutableSet()
                    when (signal) {
                        MappableSignal.OIL_PRESSURE -> gauges.add(GaugeId.OIL_PRESSURE)
                        MappableSignal.OIL_TEMP -> gauges.add(GaugeId.OIL_TEMP)
                        MappableSignal.AFR -> gauges.add(GaugeId.AFR)
                        else -> Unit
                    }
                    prefs.copy(pidAssignments = next, enabledGauges = gauges)
                }
            },
            onClear = {
                onUpdate { prefs ->
                    prefs.copy(
                        pidAssignments = prefs.pidAssignments.filterNot {
                            it.mode == entry.mode && it.id == entry.id
                        }
                    )
                }
            }
        )
    }
}

@Composable
internal fun PidLogEntryCard(
    entry: PidScanLogEntry,
    preferences: HudPreferences,
    onAssign: (MappableSignal, PidDecode) -> Unit,
    onClear: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val current = preferences.pidAssignments.firstOrNull {
        it.mode == entry.mode && it.id == entry.id
    }
    val seen = remember(entry.scannedAtMs) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.scannedAtMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HudColors.Panel)
            .border(1.dp, HudColors.Border, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.addressLabel(),
                color = HudColors.Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = current?.signal?.label ?: "seen $seen",
                color = if (current != null) HudColors.Accent else HudColors.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Text(
            text = "raw ${entry.rawHex}",
            color = HudColors.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        entry.candidates.forEach { c ->
            Text(
                text = "• ${c.decode.label}: ${c.display}",
                color = HudColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { menuOpen = true }) {
                Text(
                    text = current?.let { "Assigned: ${it.signal.label}" } ?: "Assign to…",
                    color = HudColors.Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MappableSignal.entries.forEach { signal ->
                    DropdownMenuItem(
                        text = { Text(signal.label, fontFamily = FontFamily.Monospace) },
                        onClick = {
                            menuOpen = false
                            val preferred = when (signal) {
                                MappableSignal.OIL_TEMP, MappableSignal.COOLANT, MappableSignal.IAT ->
                                    PidDecode.TEMP_A40
                                MappableSignal.OIL_PRESSURE, MappableSignal.BOOST_MAP ->
                                    PidDecode.PRESSURE_KPA
                                MappableSignal.AFR -> PidDecode.LAMBDA_AFR
                                MappableSignal.BATTERY -> PidDecode.VOLTAGE_AB
                            }
                            val decode = entry.candidates.firstOrNull { it.decode == preferred }?.decode
                                ?: preferred
                            onAssign(signal, decode)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Clear", fontFamily = FontFamily.Monospace) },
                    onClick = {
                        menuOpen = false
                        onClear()
                    }
                )
            }
            current?.let {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = {
                        Text(
                            it.decode.label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                )
            }
        }
    }
}
