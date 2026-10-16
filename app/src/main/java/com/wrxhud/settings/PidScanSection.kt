package com.wrxhud.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.wrxhud.obd.PidScanRow
import kotlinx.coroutines.launch

@Composable
fun PidScanSection(
    preferences: HudPreferences,
    connectionStatus: String,
    onUpdate: ((HudPreferences) -> HudPreferences) -> Unit,
    onScanPids: suspend () -> List<PidScanRow>
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<PidScanRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    Text(
        text = "PID SCANNER",
        color = HudColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        text = "Scan Mode 01 support + known Mode 22 probes. Assign a PID/DID to oil temp, " +
            "oil pressure, AFR, etc. Requires a live OBDLink MX+ (not simulator).",
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )

    if (preferences.pidAssignments.isNotEmpty()) {
        Text(
            text = "Mapped: " + preferences.pidAssignments.joinToString { "${it.signal.label}=${it.addressLabel()}" },
            color = HudColors.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(
            onClick = { onUpdate { it.copy(pidAssignments = emptyList()) } }
        ) {
            Text("Clear all mappings", fontFamily = FontFamily.Monospace, color = HudColors.Accent)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                scope.launch {
                    scanning = true
                    message = null
                    rows = emptyList()
                    val result = onScanPids()
                    rows = result
                    scanning = false
                    message = if (result.isEmpty()) {
                        "No results — connect MX+ with simulator off ($connectionStatus)"
                    } else {
                        "Found ${result.size} responding PIDs/DIDs"
                    }
                }
            },
            enabled = !scanning && !preferences.useSimulator,
            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent)
        ) {
            Text(
                if (scanning) "Scanning…" else "Scan PIDs",
                fontFamily = FontFamily.Monospace
            )
        }
        if (scanning) {
            CircularProgressIndicator(
                color = HudColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }

    message?.let {
        Text(
            text = it,
            color = HudColors.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    rows.forEach { row ->
        PidScanRowCard(
            row = row,
            preferences = preferences,
            onAssign = { signal, decode ->
                onUpdate { prefs ->
                    val without = prefs.pidAssignments.filterNot {
                        it.signal == signal || (it.mode == row.mode && it.id == row.id)
                    }
                    val next = without + PidAssignment(
                        signal = signal,
                        mode = row.mode,
                        id = row.id,
                        decode = decode,
                        header = row.header
                    )
                    val gauges = prefs.enabledGauges.toMutableSet()
                    when (signal) {
                        MappableSignal.OIL_PRESSURE -> gauges.add(com.wrxhud.vehicle.GaugeId.OIL_PRESSURE)
                        MappableSignal.OIL_TEMP -> gauges.add(com.wrxhud.vehicle.GaugeId.OIL_TEMP)
                        MappableSignal.AFR -> gauges.add(com.wrxhud.vehicle.GaugeId.AFR)
                        else -> Unit
                    }
                    prefs.copy(pidAssignments = next, enabledGauges = gauges)
                }
            },
            onClear = {
                onUpdate { prefs ->
                    prefs.copy(
                        pidAssignments = prefs.pidAssignments.filterNot {
                            it.mode == row.mode && it.id == row.id
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun PidScanRowCard(
    row: PidScanRow,
    preferences: HudPreferences,
    onAssign: (MappableSignal, PidDecode) -> Unit,
    onClear: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val current = preferences.pidAssignments.firstOrNull { it.mode == row.mode && it.id == row.id }

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
                text = row.addressLabel(),
                color = HudColors.Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            current?.let {
                Text(
                    text = it.signal.label,
                    color = HudColors.Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = "raw ${row.rawHex}",
            color = HudColors.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        row.candidates.take(4).forEach { c ->
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
                            val decode = row.candidates.firstOrNull { it.decode == preferred }?.decode
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
