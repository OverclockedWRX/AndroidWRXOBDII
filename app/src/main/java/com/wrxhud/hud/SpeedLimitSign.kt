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
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    val valueText = reading.limit?.toString() ?: "—"
    val unitHint = when (reading.unit) {
        SpeedUnit.MPH -> "MPH"
        SpeedUnit.KPH -> "KM/H"
    }

    Column(
        modifier = modifier
            .widthIn(min = 64.dp)
            .clip(shape)
            .background(SignWhite, shape)
            .border(3.dp, SignBorder, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LIMIT",
            color = SignBlack,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "000",
                color = Color.Transparent,
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = SignBlack,
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = if (reading.limit != null) unitHint else (reading.status ?: unitHint),
            color = SignBlack.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
