package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.MotionDisplayMode
import com.wrxhud.settings.TachVisualStyle
import com.wrxhud.vehicle.UnitConverters
import kotlin.math.min


/**
 * Full-width bar tach with frame-timed interpolation and zone coloring
 * (blue → green → amber → red toward redline), AiM-style.
 * Visual style: enhanced 3D bar, liquid water, or RPM+boost double helix.
 * Optional SPD / LIM sit to the left of the gear box.
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
    visualStyle: TachVisualStyle = TachVisualStyle.BAR_3D,
    boostPsi: Float? = null,
    maxBoostPsi: Float = 22f,
    /** Formatted speed digits; null hides the SPD tile. */
    speedValue: String? = null,
    speedUnit: String = "MPH",
    /** When non-null, shows the LIM sign left of gear. */
    speedLimit: SpeedLimitReading? = null,
    showGear: Boolean = true,
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
    val boostBlue = HudColors.BoostBlue
    val silver = HudColors.TachFill

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visualStyle) {
        if (visualStyle == TachVisualStyle.LIQUID || visualStyle == TachVisualStyle.HELIX) {
            while (true) {
                withFrameNanos { ns ->
                    phase = ((ns % 4_000_000_000L).toFloat() / 4_000_000_000f)
                }
            }
        }
    }

    val sideCluster = speedValue != null || speedLimit != null || showGear
    val tachHeight = when {
        motionDisplayMode == MotionDisplayMode.TEXT -> if (sideCluster) 112.dp else 120.dp
        motionVisibility.anyVisible -> if (sideCluster) 124.dp else 136.dp
        else -> if (sideCluster) 120.dp else 132.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(tachHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Numerals left, pitch/roll/G right — tach bar runs full-width below.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = UnitConverters.formatRpm(displayRpm),
                        color = rpmColor,
                        fontSize = if (sideCluster) 34.sp else 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RPM",
                        color = HudColors.TextDim,
                        fontSize = if (sideCluster) 10.sp else 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (motion.available && motionVisibility.anyVisible) {
                    MotionAttitudeDisplay(
                        motion = motion,
                        mode = motionDisplayMode,
                        visibility = motionVisibility,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
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
                    val fraction = min(displayRpm / maxRpm, 1f)
                    when (visualStyle) {
                        TachVisualStyle.LIQUID -> drawTachLiquid(
                            displayRpm = displayRpm,
                            maxRpm = maxRpm,
                            blueEnd = blueEnd,
                            greenEnd = greenEnd,
                            amberStart = amberStart,
                            redStart = redStart,
                            redline = redline.toFloat(),
                            phase = phase,
                            trackColor = trackColor,
                            blue = blue,
                            green = green,
                            amber = amber,
                            red = red,
                            accent = accent
                        )
                        TachVisualStyle.HELIX -> {
                            val boostFrac = ((boostPsi ?: 0f).coerceAtLeast(0f) / maxBoostPsi.coerceAtLeast(1f))
                                .coerceIn(0f, 1f)
                            drawTachHelix(
                                rpmFraction = fraction,
                                boostFraction = boostFrac,
                                phase = phase * (2f * Math.PI.toFloat()),
                                trackColor = trackColor,
                                blue = blue,
                                green = green,
                                amber = amber,
                                red = red,
                                accent = accent,
                                boostBlue = boostBlue
                            )
                        }
                        TachVisualStyle.RACE_SEG -> drawTachRaceSegments(
                            displayRpm = displayRpm,
                            maxRpm = maxRpm,
                            redline = redline.toFloat(),
                            greenZoneStart = greenEnd,
                            amberZoneStart = amberStart,
                            redZoneStart = redStart,
                            trackColor = trackColor,
                            silver = silver,
                            green = green,
                            amber = amber,
                            red = red,
                            accent = accent
                        )
                        TachVisualStyle.BAR_3D -> {
                            val depth = size.height * 0.28f
                            val trackHeight = size.height * 0.52f
                            val top = (size.height - trackHeight - depth) / 2f
                            val trackWidth = size.width - depth
                            val blueX = trackWidth * (blueEnd / maxRpm)
                            val greenX = trackWidth * (greenEnd / maxRpm)
                            val amberX = trackWidth * (amberStart / maxRpm)
                            val redX = trackWidth * (redStart / maxRpm)
                            val redlineX = trackWidth * (redline / maxRpm)
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
                            drawTach3dEnhanced(
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
                        }
                    }
                }
            }
        }

        if (sideCluster) {
            Row(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (speedValue != null) {
                    SpeedTile(
                        label = "SPD",
                        value = speedValue,
                        unit = speedUnit,
                        compact = true
                    )
                }
                if (speedLimit != null) {
                    SpeedLimitSign(reading = speedLimit, compact = true)
                }
                if (showGear) {
                    GearIndicator(gear = gear, compact = true)
                }
            }
        }
    }
}

@Composable
fun DataLogRecordButton(
    logging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (logging) HudColors.Red else HudColors.Border
    val label = if (logging) "STOP" else "REC"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudColors.Panel)
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(if (logging) RoundedCornerShape(2.dp) else CircleShape)
                .background(if (logging) HudColors.Red else HudColors.Accent)
        )
        Text(
            text = label,
            color = if (logging) HudColors.Red else HudColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
