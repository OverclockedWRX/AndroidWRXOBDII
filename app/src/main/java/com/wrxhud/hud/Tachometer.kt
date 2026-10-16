package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.UnitConverters
import kotlin.math.min

/**
 * Full-width bar tach with frame-timed interpolation and zone coloring
 * (green → amber → red toward redline), AiM-style.
 */
@Composable
fun Tachometer(
    rpm: Float?,
    previousRpm: Float?,
    sampleAgeMs: Long,
    redline: Int,
    gear: Int?,
    modifier: Modifier = Modifier
) {
    val maxRpm = (redline + 500).coerceAtLeast(7000).toFloat()
    val amberStart = redline * 0.78f
    val redStart = redline * 0.92f
    var displayRpm by remember { mutableFloatStateOf(rpm ?: 0f) }

    LaunchedEffect(rpm, previousRpm, sampleAgeMs) {
        val target = rpm ?: return@LaunchedEffect
        val from = previousRpm ?: target
        val spanNs = (sampleAgeMs.coerceIn(20L, 120L) * 1_000_000L)
        val startNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - startNs).toFloat() / spanNs).coerceIn(0f, 1f)
            displayRpm = from + (target - from) * t
            if (t >= 1f) {
                displayRpm = target
                break
            }
        }
    }

    val rpmColor = when {
        displayRpm >= redline -> HudColors.Red
        displayRpm >= redStart -> HudColors.Amber
        else -> HudColors.TextPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = UnitConverters.formatRpm(displayRpm),
                color = rpmColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackHeight = size.height * 0.7f
                    val top = (size.height - trackHeight) / 2f
                    val trackWidth = size.width
                    val amberX = trackWidth * (amberStart / maxRpm)
                    val redX = trackWidth * (redStart / maxRpm)
                    val redlineX = trackWidth * (redline / maxRpm)
                    val corner = CornerRadius(8f, 8f)

                    // Dim zone track (always visible)
                    drawRoundRect(
                        color = HudColors.TachTrack,
                        topLeft = Offset(0f, top),
                        size = Size(trackWidth, trackHeight),
                        cornerRadius = corner
                    )
                    drawRect(
                        color = HudColors.Green.copy(alpha = 0.18f),
                        topLeft = Offset(0f, top),
                        size = Size(amberX, trackHeight)
                    )
                    drawRect(
                        color = HudColors.Amber.copy(alpha = 0.18f),
                        topLeft = Offset(amberX, top),
                        size = Size(redX - amberX, trackHeight)
                    )
                    drawRect(
                        color = HudColors.Red.copy(alpha = 0.22f),
                        topLeft = Offset(redX, top),
                        size = Size(trackWidth - redX, trackHeight)
                    )

                    // Active fill with green→amber→red gradient clipped to current RPM
                    val fraction = min(displayRpm / maxRpm, 1f)
                    val fillWidth = trackWidth * fraction
                    if (fillWidth > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to HudColors.Green,
                                    (amberStart / maxRpm) to HudColors.Green,
                                    (redStart / maxRpm) to HudColors.Amber,
                                    (redline / maxRpm) to HudColors.Red,
                                    1f to HudColors.Red
                                ),
                                startX = 0f,
                                endX = trackWidth
                            ),
                            topLeft = Offset(0f, top),
                            size = Size(fillWidth, trackHeight),
                            cornerRadius = corner
                        )
                    }

                    // Zone tick marks
                    listOf(amberX, redX).forEach { x ->
                        drawLine(
                            color = Color.Black.copy(alpha = 0.45f),
                            start = Offset(x, top),
                            end = Offset(x, top + trackHeight),
                            strokeWidth = 2f
                        )
                    }
                    // Redline marker
                    drawLine(
                        color = HudColors.Accent,
                        start = Offset(redlineX, top - 4f),
                        end = Offset(redlineX, top + trackHeight + 4f),
                        strokeWidth = 3f
                    )
                }
            }
            Text(
                text = "RPM",
                color = HudColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        GearIndicator(
            gear = gear,
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxHeight()
        )
    }
}
