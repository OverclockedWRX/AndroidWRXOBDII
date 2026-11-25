package com.wrxhud.hud

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
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import androidx.compose.runtime.derivedStateOf
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
 * Optional throttle pedal % mirrors the RPM numeral + bar stack underneath.
 * Optional SPD / LIM sit to the left of the gear box.
 */
@Composable
fun Tachometer(
    rpm: Float?,
    previousRpm: Float?,
    sampleAgeMs: Long,
    redline: Int,
    gear: Int?,
    motion: () -> MotionReading = { MotionReading() },
    motionDisplayMode: MotionDisplayMode = MotionDisplayMode.RINGS,
    motionVisibility: MotionGaugeVisibility = MotionGaugeVisibility(),
    visualStyle: TachVisualStyle = TachVisualStyle.BAR_3D,
    boostPsi: Float? = null,
    maxBoostPsi: Float = 22f,
    /** Pedal / throttle position 0–100 (null → "—" when [showThrottle]). */
    throttlePercent: Float? = null,
    showThrottle: Boolean = false,
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
    // Animated values live in State objects that are read only in draw lambdas / tiny readout
    // composables, so the 60 fps interpolation never recomposes this function.
    val displayRpm = remember { mutableFloatStateOf(rpm ?: 0f) }

    LaunchedEffect(rpm, previousRpm, sampleAgeMs) {
        val target = rpm ?: return@LaunchedEffect
        val from = previousRpm ?: target
        val spanNs = (sampleAgeMs.coerceIn(20L, 120L) * 1_000_000L)
        val startNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - startNs).toFloat() / spanNs).coerceIn(0f, 1f)
            displayRpm.floatValue = from + (target - from) * t
            if (t >= 1f) {
                displayRpm.floatValue = target
                break
            }
        }
    }

    val displayThrottle = remember { mutableFloatStateOf(throttlePercent ?: 0f) }
    var previousThrottle by remember { mutableFloatStateOf(throttlePercent ?: 0f) }
    LaunchedEffect(throttlePercent) {
        val target = throttlePercent ?: return@LaunchedEffect
        val from = previousThrottle
        previousThrottle = target
        val spanNs = 80_000_000L
        val startNs = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - startNs).toFloat() / spanNs).coerceIn(0f, 1f)
            displayThrottle.floatValue = from + (target - from) * t
            if (t >= 1f) {
                displayThrottle.floatValue = target
                break
            }
        }
    }

    // Capture theme colors for the draw lambdas (draw scope is not a composition context).
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

    val phase = rememberLoopPhase(
        active = visualStyle == TachVisualStyle.LIQUID || visualStyle == TachVisualStyle.HELIX,
        periodNs = 4_000_000_000L
    )
    val palette = remember(trackColor, blue, green, amber, red, accent, panel, border, boostBlue, silver) {
        TachPalette(trackColor, blue, green, amber, red, accent, panel, border, boostBlue, silver)
    }
    val boostState = rememberUpdatedState(boostPsi)
    val maxBoostState = rememberUpdatedState(maxBoostPsi)

    // Only the availability flag is read here; the ~60 Hz readings are passed on as a
    // provider and read in small readout scopes and draw lambdas.
    val motionAvailable by remember(motion) { derivedStateOf { motion().available } }
    val showMotion = motionAvailable && motionVisibility.gForce
    val sideCluster = speedValue != null || speedLimit != null || showGear || showMotion
    val rpmValueSp = when {
        showThrottle && sideCluster -> 26.sp
        sideCluster -> 30.sp
        else -> 40.sp
    }
    val rpmLabelSp = if (sideCluster) 9.sp else 10.sp
    // Throttle numerals stay smaller; bars use equal weight so track length matches.
    val throttleValueSp = if (sideCluster) 16.sp else 20.sp
    val throttleLabelSp = if (sideCluster) 8.sp else 9.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            RpmReadout(
                present = rpm != null,
                displayRpm = displayRpm,
                redline = redline,
                redStart = redStart,
                fontSize = rpmValueSp
            )
            Text(
                text = "RPM",
                color = HudColors.TextDim,
                fontSize = rpmLabelSp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )

            Spacer(Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                RpmBar(
                    style = visualStyle,
                    palette = palette,
                    redline = redline,
                    maxRpm = maxRpm,
                    blueEnd = blueEnd,
                    greenEnd = greenEnd,
                    amberStart = amberStart,
                    redStart = redStart,
                    displayRpm = displayRpm,
                    phase = phase,
                    boostPsi = boostState,
                    maxBoostPsi = maxBoostState
                )
            }

            if (showThrottle) {
                Spacer(Modifier.height(2.dp))
                ThrottleReadout(
                    present = throttlePercent != null,
                    displayThrottle = displayThrottle,
                    fontSize = throttleValueSp
                )
                Text(
                    text = "Throttle",
                    color = HudColors.TextDim,
                    fontSize = throttleLabelSp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ThrottleBar(palette = palette, displayThrottle = displayThrottle)
                }
            }
        }

        if (sideCluster) {
            Row(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (speedValue != null || speedLimit != null) {
                    val stackCount =
                        (if (speedValue != null) 1 else 0) + (if (speedLimit != null) 1 else 0)
                    val faceH = TachSideClusterDims.stackedFaceHeight(stackCount)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TachSideClusterDims.stackGap)
                    ) {
                        if (speedValue != null) {
                            SpeedTile(
                                label = "SPD",
                                value = speedValue,
                                unit = speedUnit,
                                compact = true,
                                faceHeight = faceH
                            )
                        }
                        if (speedLimit != null) {
                            SpeedLimitSign(
                                reading = speedLimit,
                                compact = true,
                                faceHeight = faceH
                            )
                        }
                    }
                }
                if (showMotion) {
                    TachMotionCluster(
                        motion = motion,
                        mode = motionDisplayMode,
                        visibility = motionVisibility
                    )
                }
                if (showGear) {
                    // Larger than compact SPD/LIM stack.
                    GearIndicator(gear = gear, compact = false)
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

/** RPM numeral: recomposes only when the displayed (rounded) value or its zone color changes. */
@Composable
private fun RpmReadout(
    present: Boolean,
    displayRpm: State<Float>,
    redline: Int,
    redStart: Float,
    fontSize: TextUnit
) {
    val rounded by remember(displayRpm) { derivedStateOf { displayRpm.value.roundToInt() } }
    val zone by remember(displayRpm, redline, redStart) {
        derivedStateOf {
            val v = displayRpm.value
            when {
                v >= redline -> 2
                v >= redStart -> 1
                else -> 0
            }
        }
    }
    Text(
        text = UnitConverters.formatRpm(if (present) rounded.toFloat() else null),
        color = when (zone) {
            2 -> HudColors.Red
            1 -> HudColors.Amber
            else -> HudColors.TextPrimary
        },
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

/** Throttle numeral: same idea as [RpmReadout]. */
@Composable
private fun ThrottleReadout(
    present: Boolean,
    displayThrottle: State<Float>,
    fontSize: TextUnit
) {
    val rounded by remember(displayThrottle) {
        derivedStateOf { displayThrottle.value.coerceIn(0f, 100f).roundToInt() }
    }
    val zone by remember(displayThrottle) {
        derivedStateOf {
            val v = displayThrottle.value
            when {
                v >= 90f -> 2
                v >= 70f -> 1
                else -> 0
            }
        }
    }
    Text(
        text = UnitConverters.formatPercent(if (present) rounded.toFloat() else null),
        color = when {
            !present -> HudColors.TextDim
            zone == 2 -> HudColors.Red
            zone == 1 -> HudColors.Amber
            else -> HudColors.TextPrimary
        },
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

/**
 * RPM bar. All animated inputs ([displayRpm], [phase], boost) are read inside the draw
 * lambda; the Brush/Path objects are rebuilt only when the size or the cache keys change.
 */
@Composable
private fun RpmBar(
    style: TachVisualStyle,
    palette: TachPalette,
    redline: Int,
    maxRpm: Float,
    blueEnd: Float,
    greenEnd: Float,
    amberStart: Float,
    redStart: Float,
    displayRpm: State<Float>,
    phase: State<Float>,
    boostPsi: State<Float?>,
    maxBoostPsi: State<Float>
) {
    val drawModifier = remember(style, palette, redline, maxRpm) {
        Modifier.drawWithCache {
            val redlineF = redline.toFloat()
            when (style) {
                TachVisualStyle.LIQUID -> {
                    val r = LiquidTachRenderer(this, size, palette, redlineF, maxRpm, blueEnd, greenEnd, amberStart, redStart)
                    onDrawBehind { r.draw(this, displayRpm.value, phase.value) }
                }
                TachVisualStyle.HELIX -> onDrawBehind {
                    val fraction = min(displayRpm.value / maxRpm, 1f)
                    val boostFrac = ((boostPsi.value ?: 0f).coerceAtLeast(0f) / maxBoostPsi.value.coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                    drawTachHelix(
                        rpmFraction = fraction,
                        boostFraction = boostFrac,
                        phase = phase.value * (2f * Math.PI.toFloat()),
                        trackColor = palette.trackColor,
                        blue = palette.blue,
                        green = palette.green,
                        amber = palette.amber,
                        red = palette.red,
                        accent = palette.accent,
                        boostBlue = palette.boostBlue
                    )
                }
                TachVisualStyle.RACE_SEG -> {
                    val r = RaceSegTachRenderer(size, palette, redlineF, maxRpm, greenEnd, amberStart, redStart)
                    onDrawBehind { r.draw(this, displayRpm.value) }
                }
                TachVisualStyle.BAR_3D -> {
                    val stops = arrayOf(
                        0f to palette.blue,
                        (blueEnd / maxRpm) to palette.blue,
                        (greenEnd / maxRpm) to palette.green,
                        (amberStart / maxRpm) to palette.green,
                        (redStart / maxRpm) to palette.amber,
                        (redline / maxRpm) to palette.red,
                        1f to palette.red
                    )
                    val r = Tach3dRenderer(
                        size, palette,
                        blueF = blueEnd / maxRpm,
                        greenF = greenEnd / maxRpm,
                        amberF = amberStart / maxRpm,
                        redF = redStart / maxRpm,
                        redlineF = redline / maxRpm,
                        stops = stops
                    )
                    onDrawBehind { r.draw(this, min(displayRpm.value / maxRpm, 1f)) }
                }
            }
        }
    }
    Spacer(Modifier.fillMaxSize().then(drawModifier))
}

@Composable
private fun ThrottleBar(palette: TachPalette, displayThrottle: State<Float>) {
    val drawModifier = remember(palette) {
        Modifier.drawWithCache {
            val maxT = 100f
            val tBlue = 40f
            val tGreen = 65f
            val tAmber = 80f
            val tRed = 92f
            val stops = arrayOf(
                0f to palette.blue,
                (tBlue / maxT) to palette.blue,
                (tGreen / maxT) to palette.green,
                (tAmber / maxT) to palette.green,
                (tRed / maxT) to palette.amber,
                1f to palette.red
            )
            val r = Tach3dRenderer(
                size, palette,
                blueF = tBlue / maxT,
                greenF = tGreen / maxT,
                amberF = tAmber / maxT,
                redF = tRed / maxT,
                redlineF = 1f,
                stops = stops
            )
            onDrawBehind { r.draw(this, min(displayThrottle.value / maxT, 1f)) }
        }
    }
    Spacer(Modifier.fillMaxSize().then(drawModifier))
}
