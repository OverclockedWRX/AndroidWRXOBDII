package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
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
    /** Tach side-cluster tile (no outer heading; sized to [faceHeight]). */
    compact: Boolean = false,
    faceHeight: Dp = TachSideClusterDims.stackedFaceHeight(1)
) {
    val valueText = reading.limit?.toString() ?: "—"
    val unitHint = when (reading.unit) {
        SpeedUnit.MPH -> "MPH"
        SpeedUnit.KPH -> "KM/H"
    }
    val footer = if (reading.limit != null) unitHint else (reading.status ?: unitHint)

    if (compact) {
        // No outer HUD heading — sign face only; height shared with SPD / G-Force / GEAR.
        val shape = RoundedCornerShape(8.dp)
        val valueSp = if (faceHeight < 100.dp) 32.sp else 40.sp
        Box(
            modifier = modifier
                .size(width = TachSideClusterDims.width, height = faceHeight)
                .clip(shape)
                .background(SignWhite, shape)
                .border(2.dp, SignBorder, shape)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LIMIT",
                    color = SignBlack,
                    fontSize = 9.sp,
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
                    text = footer,
                    color = SignBlack.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        return
    }

    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .widthIn(min = 52.dp, max = 72.dp)
            .clip(shape)
            .background(SignWhite, shape)
            .border(2.dp, SignBorder, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LIM",
            color = SignBlack,
            fontSize = 10.sp,
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
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = SignBlack,
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = footer,
            color = SignBlack.copy(alpha = 0.7f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}
