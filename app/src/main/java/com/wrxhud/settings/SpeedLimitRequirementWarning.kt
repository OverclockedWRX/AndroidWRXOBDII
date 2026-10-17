package com.wrxhud.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.wrxhud.hud.HudColors

/** Amber callout: speed limits need GPS + network for fresh lookups; cache works offline. */
@Composable
fun SpeedLimitRequirementWarning(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HudColors.Amber.copy(alpha = 0.12f), shape)
            .border(1.dp, HudColors.Amber, shape)
            .padding(10.dp)
    ) {
        Text(
            text = "WARNING",
            color = HudColors.Amber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Speed limits need a GPS fix and a data connection to look up posted limits " +
                "from OpenStreetMap. If this road was looked up before, the cached limit still " +
                "shows when you have GPS but no data.",
            color = HudColors.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
