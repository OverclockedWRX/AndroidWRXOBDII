package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.vehicle.SpeedUnit

private val SignWhite = Color(0xFFF5F5F5)
private val SignBlack = Color(0xFF111111)
private val SignBorder = Color(0xFF1A1A1A)

/**
 * US-style posted speed limit sign for the HUD (LIMIT + digits).
 */
@Composable
fun SpeedLimitSign(
    reading: SpeedLimitReading,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(6.dp)
    val valueText = reading.limit?.toString() ?: "—"
    val unitHint = when (reading.unit) {
        SpeedUnit.MPH -> "MPH"
        SpeedUnit.KPH -> "KM/H"
    }
    val valueSp = if (compact) 22.sp else 28.sp
    val limSp = if (compact) 9.sp else 10.sp
    val minW = if (compact) 44.dp else 52.dp
    val maxW = if (compact) 60.dp else 72.dp

    Column(
        modifier = modifier
            .widthIn(min = minW, max = maxW)
            .clip(shape)
            .background(SignWhite, shape)
            .border(2.dp, SignBorder, shape)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 3.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LIM",
            color = SignBlack,
            fontSize = limSp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false
        )
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "000",
                color = Color.Transparent,
                fontSize = valueSp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = SignBlack,
                fontSize = valueSp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = if (reading.limit != null) unitHint else (reading.status ?: unitHint),
            color = SignBlack.copy(alpha = 0.7f),
            fontSize = if (compact) 7.sp else 8.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}
