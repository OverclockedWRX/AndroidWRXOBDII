package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ShiftLights(
    rpm: Float?,
    thresholds: List<Int>,
    redline: Int,
    modifier: Modifier = Modifier
) {
    val flashOn by produceState(initialValue = true, rpm, redline) {
        if (rpm != null && rpm >= redline) {
            while (isActive) {
                value = !value
                delay(80)
            }
        } else {
            value = true
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        thresholds.take(10).forEachIndexed { index, threshold ->
            val lit = rpm != null && rpm >= threshold && flashOn
            val color = when {
                !lit -> HudColors.LampOff
                index >= 8 -> HudColors.Red
                index >= 5 -> HudColors.Amber
                else -> HudColors.Green
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

fun shiftLightColor(index: Int, lit: Boolean): Color {
    if (!lit) return HudColors.LampOff
    return when {
        index >= 8 -> HudColors.Red
        index >= 5 -> HudColors.Amber
        else -> HudColors.Green
    }
}
