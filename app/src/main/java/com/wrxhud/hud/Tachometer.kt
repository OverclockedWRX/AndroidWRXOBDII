package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.MotionDisplayMode
import com.wrxhud.vehicle.UnitConverters
import kotlin.math.min

/**
 * Full-width bar tach with frame-timed interpolation and zone coloring
 * (blue → green → amber → red toward redline), AiM-style.
 * Optional faux-3D extruded bar when [threeDimensional] is enabled.
 */
@Composable
fun Tachometer(
    rpm: Float?,
    previousRpm: Float?,
    sampleAgeMs: Long,
    redline: Int,
    gear: Int?,
    motion: MotionReading = MotionReading(),
    motionDisplayMode: MotionDisplayMode = MotionDisplayMode.RINGS,
    motionVisibility: MotionGaugeVisibility = MotionGaugeVisibility(),
    threeDimensional: Boolean = false,
    modifier: Modifier = Modifier
) {
    val maxRpm = (redline + 500).coerceAtLeast(7000).toFloat()
    val blueEnd = redline * 0.45f
    val greenEnd = redline * 0.72f
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

    // Capture theme colors for Canvas (draw scope is not a composition context).
    val trackColor = HudColors.TachTrack
    val blue = HudColors.BoostBlue
    val green = HudColors.Green
    val amber = HudColors.Amber
    val red = HudColors.Red
    val accent = HudColors.Accent
    val panel = HudColors.Panel
    val border = HudColors.Border

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (motionDisplayMode == MotionDisplayMode.TEXT) 130.dp else 148.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = UnitConverters.formatRpm(displayRpm),
                        color = rpmColor,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RPM",
                        color = HudColors.TextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (motion.available && motionVisibility.anyVisible) {
                    MotionAttitudeDisplay(
                        motion = motion,
                        mode = motionDisplayMode,
                        visibility = motionVisibility,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val depth = if (threeDimensional) size.height * 0.22f else 0f
                    val trackHeight = if (threeDimensional) {
                        size.height * 0.55f
                    } else {
                        size.height * 0.7f
                    }
                    val top = (size.height - trackHeight - depth) / 2f
                    val trackWidth = size.width - depth
                    val blueX = trackWidth * (blueEnd / maxRpm)
                    val greenX = trackWidth * (greenEnd / maxRpm)
                    val amberX = trackWidth * (amberStart / maxRpm)
                    val redX = trackWidth * (redStart / maxRpm)
                    val redlineX = trackWidth * (redline / maxRpm)
                    val corner = CornerRadius(8f, 8f)
                    val fraction = min(displayRpm / maxRpm, 1f)
                    val fillWidth = trackWidth * fraction
                    val tachStops = arrayOf(
                        0f to blue,
                        (blueEnd / maxRpm) to blue,
                        (greenEnd / maxRpm) to green,
                        (amberStart / maxRpm) to green,
                        (redStart / maxRpm) to amber,
                        (redline / maxRpm) to red,
                        1f to red
                    )

                    if (threeDimensional) {
                        drawTach3d(
                            top = top,
                            trackHeight = trackHeight,
                            trackWidth = trackWidth,
                            depth = depth,
                            fillWidth = fillWidth,
                            blueX = blueX,
                            greenX = greenX,
                            amberX = amberX,
                            redX = redX,
                            redlineX = redlineX,
                            tachStops = tachStops,
                            trackColor = trackColor,
                            blue = blue,
                            green = green,
                            amber = amber,
                            red = red,
                            accent = accent,
                            panel = panel,
                            border = border
                        )
                    } else {
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(0f, top),
                            size = Size(trackWidth, trackHeight),
                            cornerRadius = corner
                        )
                        drawRect(
                            color = blue.copy(alpha = 0.18f),
                            topLeft = Offset(0f, top),
                            size = Size(blueX, trackHeight)
                        )
                        drawRect(
                            color = green.copy(alpha = 0.18f),
                            topLeft = Offset(blueX, top),
                            size = Size(greenX - blueX, trackHeight)
                        )
                        drawRect(
                            color = amber.copy(alpha = 0.18f),
                            topLeft = Offset(amberX, top),
                            size = Size(redX - amberX, trackHeight)
                        )
                        drawRect(
                            color = red.copy(alpha = 0.22f),
                            topLeft = Offset(redX, top),
                            size = Size(trackWidth - redX, trackHeight)
                        )

                        if (fillWidth > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = tachStops,
                                    startX = 0f,
                                    endX = trackWidth
                                ),
                                topLeft = Offset(0f, top),
                                size = Size(fillWidth, trackHeight),
                                cornerRadius = corner
                            )
                        }

                        listOf(blueX, greenX, amberX, redX).forEach { x ->
                            drawLine(
                                color = Color.Black.copy(alpha = 0.45f),
                                start = Offset(x, top),
                                end = Offset(x, top + trackHeight),
                                strokeWidth = 2f
                            )
                        }
                        drawLine(
                            color = accent,
                            start = Offset(redlineX, top - 4f),
                            end = Offset(redlineX, top + trackHeight + 4f),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }

        GearIndicator(
            gear = gear,
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxHeight()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTach3d(
    top: Float,
    trackHeight: Float,
    trackWidth: Float,
    depth: Float,
    fillWidth: Float,
    blueX: Float,
    greenX: Float,
    amberX: Float,
    redX: Float,
    redlineX: Float,
    tachStops: Array<Pair<Float, Color>>,
    trackColor: Color,
    blue: Color,
    green: Color,
    amber: Color,
    red: Color,
    accent: Color,
    panel: Color,
    border: Color
) {
    val frontTop = top + depth * 0.15f
    val frontBottom = frontTop + trackHeight

    val floor = Path().apply {
        moveTo(0f, frontBottom)
        lineTo(trackWidth, frontBottom)
        lineTo(trackWidth + depth, frontBottom + depth)
        lineTo(depth, frontBottom + depth)
        close()
    }
    drawPath(floor, color = Color.Black.copy(alpha = 0.55f))

    val roof = Path().apply {
        moveTo(0f, frontTop)
        lineTo(trackWidth, frontTop)
        lineTo(trackWidth + depth, frontTop + depth)
        lineTo(depth, frontTop + depth)
        close()
    }
    drawPath(
        roof,
        brush = Brush.verticalGradient(
            colors = listOf(border.copy(alpha = 0.9f), trackColor),
            startY = frontTop,
            endY = frontTop + depth
        )
    )

    val endCap = Path().apply {
        moveTo(trackWidth, frontTop)
        lineTo(trackWidth + depth, frontTop + depth)
        lineTo(trackWidth + depth, frontBottom + depth)
        lineTo(trackWidth, frontBottom)
        close()
    }
    drawPath(endCap, color = panel)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, frontTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRect(
        color = blue.copy(alpha = 0.16f),
        topLeft = Offset(0f, frontTop),
        size = Size(blueX, trackHeight)
    )
    drawRect(
        color = green.copy(alpha = 0.16f),
        topLeft = Offset(blueX, frontTop),
        size = Size(greenX - blueX, trackHeight)
    )
    drawRect(
        color = amber.copy(alpha = 0.16f),
        topLeft = Offset(amberX, frontTop),
        size = Size(redX - amberX, trackHeight)
    )
    drawRect(
        color = red.copy(alpha = 0.2f),
        topLeft = Offset(redX, frontTop),
        size = Size(trackWidth - redX, trackHeight)
    )

    if (fillWidth > 0f) {
        val fillRoof = Path().apply {
            moveTo(0f, frontTop)
            lineTo(fillWidth, frontTop)
            lineTo(fillWidth + depth, frontTop + depth)
            lineTo(depth, frontTop + depth)
            close()
        }
        drawPath(
            fillRoof,
            brush = Brush.horizontalGradient(
                colorStops = tachStops.map { (t, c) -> t to c.copy(alpha = 0.85f) }.toTypedArray(),
                startX = 0f,
                endX = trackWidth
            )
        )

        val tip = Path().apply {
            moveTo(fillWidth, frontTop)
            lineTo(fillWidth + depth, frontTop + depth)
            lineTo(fillWidth + depth, frontBottom + depth)
            lineTo(fillWidth, frontBottom)
            close()
        }
        val tipColor = when {
            fillWidth >= redX -> red
            fillWidth >= amberX -> amber
            fillWidth >= greenX -> green
            else -> blue
        }
        drawPath(
            tip,
            brush = Brush.horizontalGradient(
                colors = listOf(tipColor, tipColor.copy(alpha = 0.55f)),
                startX = fillWidth,
                endX = fillWidth + depth
            )
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = tachStops,
                startX = 0f,
                endX = trackWidth
            ),
            topLeft = Offset(0f, frontTop),
            size = Size(fillWidth, trackHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                startY = frontTop,
                endY = frontTop + trackHeight * 0.35f
            ),
            topLeft = Offset(0f, frontTop),
            size = Size(fillWidth, trackHeight * 0.35f)
        )
    }

    listOf(blueX, greenX, amberX, redX).forEach { x ->
        drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(x, frontTop),
            end = Offset(x, frontBottom),
            strokeWidth = 2f
        )
    }

    drawLine(
        color = accent,
        start = Offset(redlineX, frontTop - 3f),
        end = Offset(redlineX, frontBottom + 3f),
        strokeWidth = 3f
    )
    drawLine(
        color = accent.copy(alpha = 0.45f),
        start = Offset(redlineX, frontTop - 3f),
        end = Offset(redlineX + depth * 0.35f, frontTop - 3f + depth * 0.35f),
        strokeWidth = 2f
    )
}
