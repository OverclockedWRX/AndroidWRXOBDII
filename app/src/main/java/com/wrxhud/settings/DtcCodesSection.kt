package com.wrxhud.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.wrxhud.obd.DiagnosticTroubleCode
import com.wrxhud.obd.DtcReadResult
import kotlinx.coroutines.launch

@Composable
fun DtcCodesSection(
    useSimulator: Boolean,
    connectionStatus: String,
    onReadDtcs: suspend () -> DtcReadResult,
    onClearDtcs: suspend () -> Boolean
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DtcReadResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Text(
        text = "OBD-II CODE READER",
        color = HudColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        text = "Reads stored (Mode 03), pending (Mode 07), and permanent (Mode 0A) DTCs. " +
            "Clear uses standard Mode 04 only — it does not reflash or write calibration. " +
            "Requires a live OBDLink MX+ (not simulator).",
        color = HudColors.TextDim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp
    )
    Text(
        text = "Link: $connectionStatus",
        color = HudColors.TextPrimary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 4.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    result = onReadDtcs()
                    message = result?.message
                    busy = false
                }
            },
            enabled = !busy && !useSimulator,
            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Accent)
        ) {
            Text(if (busy) "Working…" else "Read codes", fontFamily = FontFamily.Monospace)
        }
        Button(
            onClick = { confirmClear = true },
            enabled = !busy && !useSimulator,
            colors = ButtonDefaults.buttonColors(containerColor = HudColors.Panel)
        ) {
            Text("Clear codes", color = HudColors.Amber, fontFamily = FontFamily.Monospace)
        }
        if (busy) {
            CircularProgressIndicator(
                color = HudColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }

    result?.let { r ->
        val milText = when (r.milOn) {
            true -> "MIL ON"
            false -> "MIL off"
            null -> "MIL ?"
        }
        val countText = r.confirmedCountHint?.let { " · ECU count hint $it" }.orEmpty()
        Text(
            text = "$milText$countText",
            color = if (r.milOn == true) HudColors.Red else HudColors.TextDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }

    message?.let {
        Text(
            text = it,
            color = HudColors.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }

    val codes = result?.codes.orEmpty()
    if (codes.isEmpty() && result != null) {
        Text(
            text = "No trouble codes reported.",
            color = HudColors.Green,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
    codes.forEach { code -> DtcCard(code) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = {
                Text("Clear DTCs?", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This sends SAE Mode 04 (Clear/Reset emission diagnostic information). " +
                        "Stored codes and the MIL may clear. Permanent codes can remain until " +
                        "drive-cycle monitors complete. Ignition should be ON; engine may be off.\n\n" +
                        "This does not write maps, flash the ECU, or change calibration.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            busy = true
                            message = null
                            val ok = onClearDtcs()
                            message = if (ok) {
                                "Clear sent — re-reading…"
                            } else {
                                "Clear failed — connect MX+ with simulator off"
                            }
                            if (ok) {
                                result = onReadDtcs()
                                message = "Cleared. ${result?.message ?: ""}".trim()
                            }
                            busy = false
                        }
                    }
                ) {
                    Text("Clear codes", color = HudColors.Amber, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
private fun DtcCard(code: DiagnosticTroubleCode) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HudColors.Panel)
            .border(1.dp, HudColors.Border, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = code.code,
                color = HudColors.Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = code.bank.label.uppercase(),
                color = HudColors.Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Text(
            text = code.description,
            color = HudColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}
