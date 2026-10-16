package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.AlarmState
import com.wrxhud.vehicle.LampId

@Composable
fun WarningLamps(
    alarms: AlarmState,
    enabled: Set<LampId>,
    modifier: Modifier = Modifier
) {
    val lamps = buildList {
        if (LampId.CEL in enabled) add("CEL" to alarms.cel)
        if (LampId.LOW_FUEL in enabled) add("FUEL" to alarms.lowFuel)
        if (LampId.HIGH_COOLANT in enabled) add("HOT" to alarms.highCoolant)
        if (LampId.HIGH_OIL_TEMP in enabled) add("OIL T" to alarms.highOilTemp)
        if (LampId.HIGH_IAT in enabled) add("IAT" to alarms.highIat)
        if (LampId.OVERBOOST in enabled) add("BST" to alarms.overboost)
        if (LampId.LOW_BATTERY in enabled) add("BATT" to alarms.lowBattery)
        if (LampId.LINK_LOST in enabled) add("OBD" to alarms.linkLost)
        if (LampId.GPS_LOST in enabled) add("GPS" to alarms.gpsLost)
        if (LampId.KNOCK in enabled) add("KNK" to alarms.knock)
        if (LampId.DAM in enabled) add("DAM" to alarms.dam)
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lamps.forEach { (label, on) ->
            val bg = if (on) HudColors.Red else HudColors.LampOff
            val fg = if (on) HudColors.TextPrimary else HudColors.TextDim
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .border(1.dp, HudColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
