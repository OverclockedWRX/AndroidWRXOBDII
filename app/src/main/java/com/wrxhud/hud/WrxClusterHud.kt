package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.HudPreferences
import com.wrxhud.vehicle.AlarmEvaluator
import com.wrxhud.vehicle.GaugeId
import com.wrxhud.vehicle.PressureUnit
import com.wrxhud.vehicle.SpeedUnit
import com.wrxhud.vehicle.TempUnit
import com.wrxhud.vehicle.UnitConverters
import com.wrxhud.vehicle.VehicleSnapshot
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 2025 WRX STI-style digital instrument cluster: circular tach with dedicated
 * inset boost gauge, center info stack, and circular speedometer.
 */
@Composable
fun WrxClusterHud(
    snapshot: VehicleSnapshot,
    preferences: HudPreferences,
    connectionStatus: String,
    motion: MotionReading,
    speedLimit: SpeedLimitReading,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thresholds = preferences.toAlarmThresholds()
    val useF = preferences.tempUnit == TempUnit.F
    val useMph = preferences.speedUnit == SpeedUnit.MPH
    val speedMps = snapshot.gpsSpeedMps
        ?: snapshot.obdSpeedKph?.let { UnitConverters.kphToMps(it) }
    val cherry = HudColors.Accent
    val white = HudColors.TextPrimary
    val dim = HudColors.TextDim

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1424), HudColors.Background, Color(0xFF030508))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WRX STI",
                    color = cherry,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    fontStyle = FontStyle.Italic
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preferences.showSpeedLimit && speedLimit.limit != null) {
                        Text(
                            text = "LIM ${speedLimit.limit}",
                            color = white,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = dim)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Left — tach + inset boost
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ClusterTachDial(
                        rpm = if (GaugeId.RPM in preferences.enabledGauges) snapshot.rpm else null,
                        previousRpm = snapshot.previousRpm,
                        sampleAgeMs = snapshot.rpmSampleAgeMs,
                        redline = preferences.redlineRpm,
                        boostPsi = if (GaugeId.BOOST in preferences.enabledGauges) snapshot.boostPsi else null,
                        pressureUnit = preferences.pressureUnit,
                        maxBoostPsi = preferences.boostRedPsi,
                        modifier = Modifier
                            .fillMaxHeight(0.96f)
                            .aspectRatio(1f)
                    )
                }

                // Center stack
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    ClusterCenterDisplay(
                        speedMps = speedMps,
                        useMph = useMph,
                        gear = if (GaugeId.GEAR in preferences.enabledGauges) snapshot.gear else null,
                        showSpeed = GaugeId.SPEED in preferences.enabledGauges,
                        modifier = Modifier.weight(1f)
                    )

                    if (motion.available &&
                        (preferences.showMotionPitch || preferences.showMotionRoll || preferences.showMotionGForce)
                    ) {
                        MotionAttitudeDisplay(
                            motion = motion,
                            mode = preferences.motionDisplayMode,
                            visibility = MotionGaugeVisibility(
                                pitch = preferences.showMotionPitch,
                                roll = preferences.showMotionRoll,
                                gForce = preferences.showMotionGForce
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    ClusterBottomBars(
                        coolantC = if (GaugeId.COOLANT in preferences.enabledGauges) snapshot.coolantC else null,
                        oilTempC = if (GaugeId.OIL_TEMP in preferences.enabledGauges) snapshot.oilTempC else null,
                        useF = useF,
                        coolantWarn = AlarmEvaluator.severityForCoolant(snapshot.coolantC, thresholds),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    )
                }

                // Right — speedometer
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ClusterSpeedDial(
                        speedMps = if (GaugeId.SPEED in preferences.enabledGauges) speedMps else null,
                        useMph = useMph,
                        modifier = Modifier
                            .fillMaxHeight(0.96f)
                            .aspectRatio(1f)
                    )
                }
            }

            WarningLamps(
                alarms = snapshot.alarms,
                enabled = preferences.enabledLamps,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildString {
                        append(connectionStatus)
                        if (snapshot.usingSimulator) append("  [SIM]")
                    },
                    color = dim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "2025 WRX CLUSTER",
                    color = dim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ClusterCenterDisplay(
    speedMps: Float?,
    useMph: Boolean,
    gear: Int?,
    showSpeed: Boolean,
    modifier: Modifier = Modifier
) {
    val white = HudColors.TextPrimary
    val dim = HudColors.TextDim
    val cherry = HudColors.Accent
    val speedText = if (showSpeed && speedMps != null) {
        UnitConverters.formatSpeed(speedMps, useMph)
    } else {
        ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = speedText.ifEmpty { " " },
                color = white,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
            if (showSpeed) {
                Text(
                    text = if (useMph) " mph" else " km/h",
                    color = dim,
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Lane / car graphic (cluster-style)
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp)
        ) {
            val w = size.width
            val h = size.height
            val lane = white.copy(alpha = 0.55f)
            drawLine(lane, Offset(w * 0.15f, h), Offset(w * 0.35f, 0f), 2.5f)
            drawLine(lane, Offset(w * 0.85f, h), Offset(w * 0.65f, 0f), 2.5f)
            // Simple top-down WRX silhouette
            val cx = w / 2f
            val cy = h * 0.55f
            val body = Path().apply {
                moveTo(cx, cy - h * 0.32f)
                lineTo(cx + w * 0.07f, cy - h * 0.12f)
                lineTo(cx + w * 0.08f, cy + h * 0.28f)
                lineTo(cx - w * 0.08f, cy + h * 0.28f)
                lineTo(cx - w * 0.07f, cy - h * 0.12f)
                close()
            }
            drawPath(body, white.copy(alpha = 0.9f))
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .border(1.5.dp, white.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = UnitConverters.formatGear(gear),
                color = white,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "MODE",
            color = cherry.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ClusterBottomBars(
    coolantC: Float?,
    oilTempC: Float?,
    useF: Boolean,
    coolantWarn: AlarmEvaluator.Severity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coolantC != null) {
            ClusterTempBar(
                labelL = "C",
                labelR = "H",
                fraction = ((coolantC - 60f) / 60f).coerceIn(0f, 1f),
                warn = coolantWarn != AlarmEvaluator.Severity.NORMAL &&
                    coolantWarn != AlarmEvaluator.Severity.NONE,
                valueText = UnitConverters.formatTemp(coolantC, useF) + if (useF) "°F" else "°C",
                modifier = Modifier.weight(1f)
            )
        }
        if (oilTempC != null) {
            ClusterTempBar(
                labelL = "OIL",
                labelR = "",
                fraction = ((oilTempC - 60f) / 70f).coerceIn(0f, 1f),
                warn = oilTempC >= 120f,
                valueText = UnitConverters.formatTemp(oilTempC, useF) + if (useF) "°F" else "°C",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ClusterTempBar(
    labelL: String,
    labelR: String,
    fraction: Float,
    warn: Boolean,
    valueText: String,
    modifier: Modifier = Modifier
) {
    val white = HudColors.TextPrimary
    val dim = HudColors.TextDim
    val fill = if (warn) HudColors.Accent else white
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(labelL, color = dim, fontSize = 9.sp, fontFamily = FontFamily.SansSerif)
            Text(valueText, color = fill, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            if (labelR.isNotEmpty()) {
                Text(labelR, color = dim, fontSize = 9.sp, fontFamily = FontFamily.SansSerif)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            drawRoundRect(Color(0xFF2A3038), cornerRadius = CornerRadius(3.dp.toPx()))
            val w = size.width * fraction
            if (w > 0f) {
                drawRoundRect(
                    fill,
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
            // Hot tip
            drawRect(HudColors.Accent, topLeft = Offset(size.width * 0.88f, 0f), size = Size(size.width * 0.12f, size.height))
        }
    }
}

@Composable
private fun ClusterTachDial(
    rpm: Float?,
    previousRpm: Float?,
    sampleAgeMs: Long,
    redline: Int,
    boostPsi: Float?,
    pressureUnit: PressureUnit,
    maxBoostPsi: Float,
    modifier: Modifier = Modifier
) {
    val maxRpm = (redline + 500).coerceAtLeast(8000).toFloat()
    var displayRpm by remember { mutableFloatStateOf(rpm ?: 0f) }
    var peakBoostPsi by remember { mutableFloatStateOf(boostPsi ?: 0f) }

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
    LaunchedEffect(boostPsi) {
        val b = boostPsi ?: return@LaunchedEffect
        if (b > peakBoostPsi) peakBoostPsi = b
    }

    val cherry = HudColors.Accent
    val white = HudColors.TextPrimary
    val dim = HudColors.TextDim
    val track = HudColors.TachTrack
    val face = Color(0xFF0A0C12)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(size.width, size.height) * 0.46f
            drawClusterDialFace(cx, cy, r, face, track, cherry, white)

            // Major ticks 0..8 x1000
            val majorCount = (maxRpm / 1000f).toInt().coerceIn(6, 9)
            val startAng = 135f
            val sweep = 270f
            for (i in 0..majorCount) {
                val frac = i / majorCount.toFloat()
                val ang = Math.toRadians((startAng + sweep * frac).toDouble())
                val outer = Offset(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
                val inner = Offset(cx + r * 0.86f * cos(ang).toFloat(), cy + r * 0.86f * sin(ang).toFloat())
                drawLine(white, inner, outer, strokeWidth = 3.5f, cap = StrokeCap.Round)
                // half ticks
                if (i < majorCount) {
                    val mid = Math.toRadians((startAng + sweep * (frac + 0.5f / majorCount)).toDouble())
                    val o2 = Offset(cx + r * cos(mid).toFloat(), cy + r * sin(mid).toFloat())
                    val i2 = Offset(cx + r * 0.90f * cos(mid).toFloat(), cy + r * 0.90f * sin(mid).toFloat())
                    drawLine(white.copy(alpha = 0.7f), i2, o2, strokeWidth = 2f)
                }
            }

            // Redline arc
            val redFrac = (redline / maxRpm).coerceIn(0f, 1f)
            drawArc(
                color = cherry,
                startAngle = startAng + sweep * redFrac,
                sweepAngle = sweep * (1f - redFrac),
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = r * 0.07f, cap = StrokeCap.Butt)
            )

            // RPM needle
            val rpmFrac = (displayRpm / maxRpm).coerceIn(0f, 1f)
            drawNeedle(cx, cy, r * 0.82f, startAng + sweep * rpmFrac, cherry, 4f)

            // Inset boost gauge (lower-left, like factory cluster)
            drawBoostInset(
                parentCx = cx,
                parentCy = cy,
                parentR = r,
                boostPsi = boostPsi,
                peakPsi = peakBoostPsi,
                maxBoostPsi = maxBoostPsi.coerceAtLeast(15f),
                cherry = cherry,
                white = white,
                face = face
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (rpm != null) UnitConverters.formatRpm(displayRpm) else "",
                color = white,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "x1000 r/min",
                color = dim,
                fontSize = 9.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        // Boost digital + peak (cluster-style, over lower-left inset)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "BOOST",
                color = dim,
                fontSize = 8.sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = UnitConverters.formatPressure(boostPsi, pressureUnit),
                color = white,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Pk ${UnitConverters.formatPressure(peakBoostPsi, pressureUnit)}",
                color = dim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = pressureUnit.name.lowercase(),
                color = dim,
                fontSize = 8.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun ClusterSpeedDial(
    speedMps: Float?,
    useMph: Boolean,
    modifier: Modifier = Modifier
) {
    val maxSpeed = if (useMph) 180f else 280f
    val value = when {
        speedMps == null -> 0f
        useMph -> UnitConverters.mpsToMph(speedMps)
        else -> UnitConverters.mpsToKph(speedMps)
    }
    var display by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) {
        val from = display
        val startNs = withFrameNanos { it }
        val spanNs = 80_000_000L
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - startNs).toFloat() / spanNs).coerceIn(0f, 1f)
            display = from + (value - from) * t
            if (t >= 1f) break
        }
    }

    val cherry = HudColors.Accent
    val white = HudColors.TextPrimary
    val dim = HudColors.TextDim
    val track = HudColors.TachTrack
    val face = Color(0xFF0A0C12)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(size.width, size.height) * 0.46f
            drawClusterDialFace(cx, cy, r, face, track, cherry, white)

            val startAng = 135f
            val sweep = 270f
            val majors = if (useMph) 9 else 14 // 0..180 / 20 or 0..280 / 20
            for (i in 0..majors) {
                val frac = i / majors.toFloat()
                val ang = Math.toRadians((startAng + sweep * frac).toDouble())
                val outer = Offset(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
                val inner = Offset(cx + r * 0.86f * cos(ang).toFloat(), cy + r * 0.86f * sin(ang).toFloat())
                drawLine(white, inner, outer, strokeWidth = 3.2f, cap = StrokeCap.Round)
            }

            val frac = (display / maxSpeed).coerceIn(0f, 1f)
            if (speedMps != null) {
                drawNeedle(cx, cy, r * 0.82f, startAng + sweep * frac, cherry, 4f)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "STI",
                color = cherry,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = if (useMph) "mph" else "km/h",
                color = dim,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

private fun DrawScope.drawClusterDialFace(
    cx: Float,
    cy: Float,
    r: Float,
    face: Color,
    track: Color,
    cherry: Color,
    white: Color
) {
    // Soft brushed face
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1E28), face, Color(0xFF05060A)),
            center = Offset(cx, cy),
            radius = r
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(track, radius = r * 0.98f, center = Offset(cx, cy), style = Stroke(r * 0.04f))
    // Cherry inner glow ring
    drawCircle(
        color = cherry.copy(alpha = 0.85f),
        radius = r * 0.92f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.018f)
    )
    drawCircle(
        color = white.copy(alpha = 0.15f),
        radius = r * 0.12f,
        center = Offset(cx, cy)
    )
    drawCircle(cherry, radius = r * 0.035f, center = Offset(cx, cy))
}

private fun DrawScope.drawNeedle(
    cx: Float,
    cy: Float,
    length: Float,
    angleDeg: Float,
    color: Color,
    stroke: Float
) {
    val ang = Math.toRadians(angleDeg.toDouble())
    val tip = Offset(cx + length * cos(ang).toFloat(), cy + length * sin(ang).toFloat())
    val tail = Offset(cx - length * 0.12f * cos(ang).toFloat(), cy - length * 0.12f * sin(ang).toFloat())
    drawLine(color, tail, tip, strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * Factory-style boost inset: vacuum→boost sweep in the lower-left of the tach.
 * Scale mirrors the cluster (−1…+2 ×100 kPa ≈ −14.5…+29 psi).
 */
private fun DrawScope.drawBoostInset(
    parentCx: Float,
    parentCy: Float,
    parentR: Float,
    boostPsi: Float?,
    peakPsi: Float,
    maxBoostPsi: Float,
    cherry: Color,
    white: Color,
    face: Color
) {
    val bx = parentCx - parentR * 0.38f
    val by = parentCy + parentR * 0.28f
    val br = parentR * 0.28f

    drawCircle(face.copy(alpha = 0.95f), radius = br * 1.05f, center = Offset(bx, by))
    drawCircle(white.copy(alpha = 0.25f), radius = br, center = Offset(bx, by), style = Stroke(2f))

    val startAng = 200f
    val sweep = 160f
    drawArc(
        color = white.copy(alpha = 0.35f),
        startAngle = startAng,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(bx - br, by - br),
        size = Size(br * 2, br * 2),
        style = Stroke(width = 3f)
    )

    // Map psi: vacuum floor -14.5 → boost max
    val minPsi = -14.5f
    val maxPsi = maxBoostPsi.coerceAtLeast(22f)
    val psi = (boostPsi ?: 0f).coerceIn(minPsi, maxPsi)
    val frac = ((psi - minPsi) / (maxPsi - minPsi)).coerceIn(0f, 1f)
    drawNeedle(bx, by, br * 0.85f, startAng + sweep * frac, cherry, 2.5f)

    // Peak tick
    val peakFrac = ((peakPsi.coerceIn(minPsi, maxPsi) - minPsi) / (maxPsi - minPsi)).coerceIn(0f, 1f)
    val pang = Math.toRadians((startAng + sweep * peakFrac).toDouble())
    val po = Offset(bx + br * 0.95f * cos(pang).toFloat(), by + br * 0.95f * sin(pang).toFloat())
    val pi = Offset(bx + br * 0.78f * cos(pang).toFloat(), by + br * 0.78f * sin(pang).toFloat())
    drawLine(white.copy(alpha = 0.8f), pi, po, strokeWidth = 2f)
}
