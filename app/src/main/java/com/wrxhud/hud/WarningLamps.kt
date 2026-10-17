package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.AlarmState
import com.wrxhud.vehicle.LampId

/** Visual tone for a status lamp. */
private enum class LampTone {
    /** Healthy / connected (e.g. GPS fix, OBD link). */
    GOOD,
    /** No affirmative status. */
    IDLE,
    /** Degraded / caution. */
    WARN,
    /** Fault / alarm active. */
    FAULT
}

private data class LampUi(val label: String, val tone: LampTone)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarningLamps(
    alarms: AlarmState,
    enabled: Set<LampId>,
    modifier: Modifier = Modifier
) {
    val lamps = buildList {
        // Alarm lamps: idle when clear, warn/fault when set.
        if (LampId.CEL in enabled) {
            add(LampUi("CEL", if (alarms.cel) LampTone.FAULT else LampTone.IDLE))
        }
        if (LampId.LOW_FUEL in enabled) {
            add(LampUi("FUEL", if (alarms.lowFuel) LampTone.WARN else LampTone.IDLE))
        }
        if (LampId.HIGH_COOLANT in enabled) {
            add(LampUi("HOT", if (alarms.highCoolant) LampTone.FAULT else LampTone.IDLE))
        }
        if (LampId.HIGH_OIL_TEMP in enabled) {
            add(LampUi("OIL T", if (alarms.highOilTemp) LampTone.FAULT else LampTone.IDLE))
        }
        if (LampId.HIGH_IAT in enabled) {
            add(LampUi("IAT", if (alarms.highIat) LampTone.WARN else LampTone.IDLE))
        }
        if (LampId.OVERBOOST in enabled) {
            add(LampUi("BST", if (alarms.overboost) LampTone.FAULT else LampTone.IDLE))
        }
        if (LampId.LOW_BATTERY in enabled) {
            add(LampUi("BATT", if (alarms.lowBattery) LampTone.WARN else LampTone.IDLE))
        }
        if (LampId.LOW_OIL_PRESSURE in enabled) {
            add(LampUi("OIL P", if (alarms.lowOilPressure) LampTone.FAULT else LampTone.IDLE))
        }
        // Link lamps: green when healthy, red when lost.
        if (LampId.LINK_LOST in enabled) {
            add(LampUi("OBD", if (alarms.linkLost) LampTone.FAULT else LampTone.GOOD))
        }
        if (LampId.GPS_LOST in enabled) {
            add(LampUi("GPS", if (alarms.gpsLost) LampTone.FAULT else LampTone.GOOD))
        }
        // KNK / DAM intentionally omitted — not live-wired on stock OBD.
    }

    FlowRow(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lamps.forEach { lamp ->
            val (bg, fg) = lampColors(lamp.tone)
            Text(
                text = lamp.label,
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .border(1.dp, HudColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

private fun lampColors(tone: LampTone): Pair<Color, Color> = when (tone) {
    LampTone.GOOD -> HudColors.Green to HudColors.Background
    LampTone.IDLE -> HudColors.LampOff to HudColors.TextDim
    LampTone.WARN -> HudColors.Amber to HudColors.Background
    LampTone.FAULT -> HudColors.Red to HudColors.TextPrimary
}
