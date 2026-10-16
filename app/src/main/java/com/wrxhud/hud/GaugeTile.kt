package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.AlarmEvaluator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun GaugeTile(
    label: String,
    valueText: String,
    unit: String,
    severity: AlarmEvaluator.Severity,
    modifier: Modifier = Modifier
) {
    val flashOn by produceState(initialValue = true, severity) {
        if (severity == AlarmEvaluator.Severity.CRITICAL) {
            while (isActive) {
                value = !value
                delay(120)
            }
        } else {
            value = true
        }
    }

    val valueColor = when {
        severity == AlarmEvaluator.Severity.CRITICAL && !flashOn -> HudColors.TextDim
        severity == AlarmEvaluator.Severity.CRITICAL -> HudColors.Red
        severity == AlarmEvaluator.Severity.WARN -> HudColors.Amber
        else -> HudColors.TextPrimary
    }

    Column(
        modifier = modifier
            .widthIn(min = 100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HudColors.Panel)
            .border(1.dp, HudColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = valueText,
            color = valueColor,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            color = HudColors.TextDim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SpeedTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudColors.Panel)
            .border(1.dp, HudColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = HudColors.TextPrimary,
            fontSize = 48.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            color = HudColors.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

fun severityColor(severity: AlarmEvaluator.Severity): Color = when (severity) {
    AlarmEvaluator.Severity.CRITICAL -> HudColors.Red
    AlarmEvaluator.Severity.WARN -> HudColors.Amber
    AlarmEvaluator.Severity.NORMAL -> HudColors.TextPrimary
    AlarmEvaluator.Severity.NONE -> HudColors.TextDim
}
