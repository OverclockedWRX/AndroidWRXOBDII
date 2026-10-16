package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.UnitConverters
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

/**
 * Manifold relative pressure: positive = boost (blue-shaded panel), negative = vacuum (plain).
 */
@Composable
fun BoostVacTile(
    psi: Float?,
    unit: PressureUnit,
    severity: AlarmEvaluator.Severity,
    modifier: Modifier = Modifier
) {
    val inBoost = psi != null && psi > 0f
    val label = when {
        psi == null -> "BOOST"
        inBoost -> "BOOST"
        else -> "VACUUM"
    }
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
    val shape = RoundedCornerShape(8.dp)
    val panelColor = if (inBoost) {
        lerp(HudColors.Panel, HudColors.BoostBlue.copy(alpha = 0.45f), 0.55f)
    } else {
        HudColors.Panel
    }
    val borderColor = if (inBoost) HudColors.BoostBlue else HudColors.Border

    Column(
        modifier = modifier
            .widthIn(min = 100.dp)
            .clip(shape)
            .background(panelColor, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = if (inBoost) HudColors.BoostBlue else HudColors.TextDim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = UnitConverters.formatPressure(psi, unit),
            color = valueColor,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit.name,
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
        // Reserve width for three digits so 9↔99↔100 does not reflow the HUD.
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "000",
                color = Color.Transparent,
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = HudColors.TextPrimary,
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
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
