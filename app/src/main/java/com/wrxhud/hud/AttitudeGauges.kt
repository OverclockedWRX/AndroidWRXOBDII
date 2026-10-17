package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.sensors.MotionReading
import com.wrxhud.sensors.formatGForce
import com.wrxhud.sensors.formatSignedDegrees
import com.wrxhud.settings.MotionDisplayMode
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ring colors matching the inclinometer reference (lime upper / cyan lower). */
private val RingGreen = Color(0xFFB8F000)
private val RingCyan = Color(0xFF00E5FF)
private val CarGlow = Color(0xFFE8FF4A)
private val HorizonLine = Color(0xFF94A3B8)

data class MotionGaugeVisibility(
    val pitch: Boolean = true,
    val roll: Boolean = true,
    val gForce: Boolean = true
) {
    val anyVisible: Boolean get() = pitch || roll || gForce
}

/**
 * Pitch / roll / G — text strip, WRX rings (+ G ring), or combined attitude.
 */
@Composable
fun MotionAttitudeDisplay(
    motion: MotionReading,
    mode: MotionDisplayMode,
    visibility: MotionGaugeVisibility = MotionGaugeVisibility(),
    modifier: Modifier = Modifier
) {
    if (!visibility.anyVisible) return
    when (mode) {
        MotionDisplayMode.TEXT -> MotionTelemetryStrip(
            motion = motion,
            visibility = visibility,
            modifier = modifier
        )
        MotionDisplayMode.RINGS -> DualAttitudeRings(
            motion = motion,
            visibility = visibility,
            modifier = modifier
        )
        MotionDisplayMode.COMBINED -> CombinedAttitudeRing(
            motion = motion,
            visibility = visibility,
            modifier = modifier
        )
    }
}

@Composable
fun MotionTelemetryStrip(
    motion: MotionReading,
    visibility: MotionGaugeVisibility = MotionGaugeVisibility(),
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visibility.pitch) MotionCell("PIT", formatSignedDegrees(motion.pitchDeg))
        if (visibility.roll) MotionCell("ROL", formatSignedDegrees(motion.rollDeg))
        if (visibility.gForce) MotionCell("G", formatGForce(motion.gForce))
    }
}

@Composable
private fun MotionCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = HudColors.TextPrimary,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DualAttitudeRings(
    motion: MotionReading,
    visibility: MotionGaugeVisibility,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (visibility.pitch) {
            InclinometerGauge(
                degrees = motion.pitchDeg,
                kind = InclinometerKind.PITCH,
                size = 112.dp
            )
        }
        if (visibility.roll) {
            InclinometerGauge(
                degrees = motion.rollDeg,
                kind = InclinometerKind.ROLL,
                size = 112.dp
            )
        }
        if (visibility.gForce) {
            GForceGauge(
                motion = motion,
                size = 112.dp
            )
        }
    }
}

@Composable
private fun CombinedAttitudeRing(
    motion: MotionReading,
    visibility: MotionGaugeVisibility,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        when {
            visibility.pitch && visibility.roll -> {
                InclinometerGauge(
                    degrees = motion.pitchDeg,
                    secondaryDegrees = motion.rollDeg,
                    kind = InclinometerKind.COMBINED,
                    size = 120.dp
                )
            }
            visibility.pitch -> {
                InclinometerGauge(
                    degrees = motion.pitchDeg,
                    kind = InclinometerKind.PITCH,
                    size = 112.dp
                )
            }
            visibility.roll -> {
                InclinometerGauge(
                    degrees = motion.rollDeg,
                    kind = InclinometerKind.ROLL,
                    size = 112.dp
                )
            }
        }
        if (visibility.gForce) {
            GForceGauge(motion = motion, size = 112.dp)
        }
        if (visibility.pitch || visibility.roll) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                if (visibility.pitch) TinyMetric("Pitch", formatSignedDegrees(motion.pitchDeg))
                if (visibility.roll) TinyMetric("Roll", formatSignedDegrees(motion.rollDeg))
            }
        }
    }
}

private const val G_TRAIL_MAX = 56
private const val G_FULL_SCALE = 1.5f

@Composable
private fun GForceGauge(
    motion: MotionReading,
    size: Dp
) {
    val trail = remember { mutableStateListOf<Offset>() }
    val lat = motion.lateralG
    val lon = motion.longitudinalG
    SideEffect {
        if (lat != null && lon != null) {
            val next = Offset(lat, lon)
            val prev = trail.lastOrNull()
            if (prev == null || hypot((prev.x - next.x).toDouble(), (prev.y - next.y).toDouble()) > 0.008) {
                trail.add(next)
                while (trail.size > G_TRAIL_MAX) trail.removeAt(0)
            }
        }
    }
    val trailSnapshot = trail.toList()

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGForceFace(
                lateralG = lat ?: 0f,
                longitudinalG = lon ?: 0f,
                hasSample = lat != null && lon != null,
                trail = trailSnapshot
            )
        }
        Text(
            text = formatGForce(motion.gForce),
            color = CarGlow,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = size * 0.18f)
        )
    }
}

private fun DrawScope.drawGForceFace(
    lateralG: Float,
    longitudinalG: Float,
    hasSample: Boolean,
    trail: List<Offset>
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f - 3.dp.toPx()
    val plotR = r * 0.72f

    drawCircle(color = Color.Black, radius = r * 0.92f, center = Offset(cx, cy))
    drawSegmentedRing(cx, cy, r)

    // Scale rings at 0.5g / 1.0g / full scale
    for (frac in listOf(0.5f / G_FULL_SCALE, 1f / G_FULL_SCALE, 1f)) {
        drawCircle(
            color = HorizonLine.copy(alpha = 0.22f),
            radius = plotR * frac,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )
    }
    // Crosshairs
    drawLine(
        color = HorizonLine.copy(alpha = 0.45f),
        start = Offset(cx - plotR, cy),
        end = Offset(cx + plotR, cy),
        strokeWidth = 1.2.dp.toPx()
    )
    drawLine(
        color = HorizonLine.copy(alpha = 0.45f),
        start = Offset(cx, cy - plotR),
        end = Offset(cx, cy + plotR),
        strokeWidth = 1.2.dp.toPx()
    )

    fun toPlot(sample: Offset): Offset {
        val x = (sample.x / G_FULL_SCALE).coerceIn(-1.15f, 1.15f)
        val y = (-sample.y / G_FULL_SCALE).coerceIn(-1.15f, 1.15f) // +longitudinal → up
        return Offset(cx + x * plotR, cy + y * plotR)
    }

    // History trail (older = dimmer)
    if (trail.size >= 2) {
        val path = Path()
        trail.forEachIndexed { i, sample ->
            val p = toPlot(sample)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path,
            color = RingCyan.copy(alpha = 0.55f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        trail.forEachIndexed { i, sample ->
            val alpha = 0.15f + 0.55f * (i + 1).toFloat() / trail.size
            drawCircle(
                color = CarGlow.copy(alpha = alpha),
                radius = 1.6.dp.toPx(),
                center = toPlot(sample)
            )
        }
    }

    if (hasSample) {
        val tip = toPlot(Offset(lateralG, longitudinalG))
        drawCircle(color = CarGlow.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = tip)
        drawCircle(color = CarGlow, radius = 3.5.dp.toPx(), center = tip)
        drawCircle(color = Color.Black, radius = 1.4.dp.toPx(), center = tip)
    }
}

@Composable
private fun TinyMetric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = HudColors.TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(
            value,
            color = HudColors.TextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

private enum class InclinometerKind { PITCH, ROLL, COMBINED }

@Composable
private fun InclinometerGauge(
    degrees: Float?,
    kind: InclinometerKind,
    size: Dp,
    secondaryDegrees: Float? = null
) {
    val deg = degrees ?: 0f
    val roll = secondaryDegrees ?: 0f
    val valueLabel = when (kind) {
        InclinometerKind.COMBINED -> {
            val p = degrees?.roundToInt()
            val r = secondaryDegrees?.roundToInt()
            if (p == null && r == null) "—"
            else "P${signed(p)} R${signed(r)}"
        }
        else -> if (degrees == null) "—" else "${signed(degrees.roundToInt())}°"
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawInclinometerFace(
                kind = kind,
                pitchDeg = deg,
                rollDeg = if (kind == InclinometerKind.COMBINED) roll else deg
            )
        }
        Text(
            text = valueLabel,
            color = CarGlow,
            fontSize = if (kind == InclinometerKind.COMBINED) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = size * 0.18f)
        )
    }
}

private fun signed(v: Int?): String {
    if (v == null) return "—"
    return if (v > 0) "+$v" else "$v"
}

private fun DrawScope.drawInclinometerFace(
    kind: InclinometerKind,
    pitchDeg: Float,
    rollDeg: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f - 3.dp.toPx()

    drawCircle(color = Color.Black, radius = r * 0.92f, center = Offset(cx, cy))
    drawSegmentedRing(cx, cy, r)

    // Horizon reference through 0°
    drawLine(
        color = HorizonLine.copy(alpha = 0.55f),
        start = Offset(cx - r * 0.72f, cy),
        end = Offset(cx + r * 0.72f, cy),
        strokeWidth = 1.2.dp.toPx()
    )

    // Bias car slightly below center so the degree readout above stays clear.
    // Pivot at Offset.Zero so tilt/roll spins the silhouette in place (default
    // rotate() pivot is the canvas center and swung the car out of the ring).
    val carOrigin = Offset(cx, cy + r * 0.08f)
    translate(carOrigin.x, carOrigin.y) {
        when (kind) {
            InclinometerKind.PITCH -> {
                // Screen-remapped pitch: positive = nose up → clockwise for side-view nose-right.
                val tilt = pitchDeg.coerceIn(-45f, 45f)
                rotate(degrees = tilt, pivot = Offset.Zero) {
                    drawWrxVbSide(scale = r * 0.058f)
                }
            }
            InclinometerKind.ROLL -> {
                // Screen-remapped roll: positive = lean right → clockwise for rear view.
                val tilt = rollDeg.coerceIn(-45f, 45f)
                rotate(degrees = tilt, pivot = Offset.Zero) {
                    drawWrxVbRear(scale = r * 0.058f)
                }
            }
            InclinometerKind.COMBINED -> {
                val p = pitchDeg.coerceIn(-35f, 35f)
                val ro = rollDeg.coerceIn(-35f, 35f)
                rotate(degrees = ro, pivot = Offset.Zero) {
                    val foreshorten = (1f - p / 100f).coerceIn(0.65f, 1.15f)
                    scale(scaleX = 1f, scaleY = foreshorten, pivot = Offset.Zero) {
                        drawWrxVbRear(scale = r * 0.042f)
                    }
                }
            }
        }
    }
}

/** Segmented lime (upper) / cyan (lower) ring like the reference inclinometer. */
private fun DrawScope.drawSegmentedRing(cx: Float, cy: Float, r: Float) {
    val segments = 52
    val sweep = 360f / segments
    val gap = sweep * 0.28f
    val stroke = Stroke(width = r * 0.11f, cap = StrokeCap.Butt)
    val inset = r * 0.04f
    for (i in 0 until segments) {
        val start = -90f + i * sweep + gap / 2f
        val mid = start + (sweep - gap) / 2f
        val rad = Math.toRadians(mid.toDouble())
        // Android angles: 0° = 3 o'clock, clockwise; sin>0 → below center.
        val isUpper = sin(rad) < 0.0
        val color = if (isUpper) RingGreen else RingCyan
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep - gap,
            useCenter = false,
            topLeft = Offset(cx - r + inset, cy - r + inset),
            size = Size((r - inset) * 2f, (r - inset) * 2f),
            style = stroke
        )
    }
}

/**
 * 2022+ WRX VB side silhouette — sedan rake, hood scoop, trunk wing, square-ish nose.
 * Nose points +X; glow yellow-green.
 */
private fun DrawScope.drawWrxVbSide(scale: Float) {
    val s = scale
    val body = Path().apply {
        // Rocker
        moveTo(-9.2f * s, 2.4f * s)
        // Rear bumper → wing mount
        lineTo(-8.6f * s, 1.2f * s)
        lineTo(-7.8f * s, -0.2f * s)
        // Trunk / wing
        lineTo(-6.2f * s, -1.0f * s)
        lineTo(-5.4f * s, -2.4f * s) // wing tip
        lineTo(-4.6f * s, -1.2f * s)
        // Roof
        lineTo(-1.2f * s, -2.6f * s)
        lineTo(2.8f * s, -2.5f * s)
        // A-pillar / hood
        lineTo(4.2f * s, -1.1f * s)
        // Hood scoop bump
        lineTo(5.0f * s, -1.35f * s)
        lineTo(6.2f * s, -1.0f * s)
        lineTo(7.4f * s, -0.35f * s)
        // Nose
        lineTo(9.0f * s, 0.55f * s)
        lineTo(9.1f * s, 2.4f * s)
        close()
    }
    drawPath(
        body,
        brush = Brush.verticalGradient(
            colors = listOf(CarGlow, CarGlow.copy(alpha = 0.75f)),
            startY = -3f * s,
            endY = 3f * s
        )
    )
    // Cabin glass
    drawPath(
        Path().apply {
            moveTo(-3.8f * s, -0.4f * s)
            lineTo(-1.0f * s, -2.2f * s)
            lineTo(2.4f * s, -2.1f * s)
            lineTo(3.6f * s, -0.5f * s)
            close()
        },
        color = Color(0xFF0A0A0A).copy(alpha = 0.55f)
    )
    // Soft glow outline
    drawPath(body, color = CarGlow.copy(alpha = 0.35f), style = Stroke(width = 1.4.dp.toPx()))
    // Wheels
    drawCircle(Color.Black.copy(alpha = 0.9f), 1.85f * s, Offset(-5.2f * s, 2.85f * s))
    drawCircle(Color.Black.copy(alpha = 0.9f), 1.85f * s, Offset(5.0f * s, 2.85f * s))
    drawCircle(CarGlow.copy(alpha = 0.4f), 0.7f * s, Offset(-5.2f * s, 2.85f * s))
    drawCircle(CarGlow.copy(alpha = 0.4f), 0.7f * s, Offset(5.0f * s, 2.85f * s))
}

/**
 * WRX VB rear / 3/4 rear — wide fenders, roof spoiler / trunk wing, hexagonal cues.
 */
private fun DrawScope.drawWrxVbRear(scale: Float) {
    val s = scale
    val body = Path().apply {
        moveTo(-7.2f * s, 2.6f * s)
        lineTo(-6.4f * s, 0.2f * s)
        lineTo(-5.2f * s, -1.6f * s)
        // Wing
        lineTo(-4.0f * s, -2.0f * s)
        lineTo(-3.2f * s, -3.2f * s)
        lineTo(3.2f * s, -3.2f * s)
        lineTo(4.0f * s, -2.0f * s)
        lineTo(5.2f * s, -1.6f * s)
        lineTo(6.4f * s, 0.2f * s)
        lineTo(7.2f * s, 2.6f * s)
        lineTo(5.0f * s, 3.2f * s)
        lineTo(-5.0f * s, 3.2f * s)
        close()
    }
    drawPath(
        body,
        brush = Brush.verticalGradient(
            colors = listOf(CarGlow, CarGlow.copy(alpha = 0.7f)),
            startY = -3.5f * s,
            endY = 3.5f * s
        )
    )
    // Rear glass
    drawRoundRect(
        color = Color(0xFF0A0A0A).copy(alpha = 0.55f),
        topLeft = Offset(-3.4f * s, -1.8f * s),
        size = Size(6.8f * s, 2.6f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.6f * s, 0.6f * s)
    )
    // Wing bar glow
    drawLine(
        color = CarGlow,
        start = Offset(-3.4f * s, -3.25f * s),
        end = Offset(3.4f * s, -3.25f * s),
        strokeWidth = 2.2f * s.coerceAtLeast(1f)
    )
    drawPath(body, color = CarGlow.copy(alpha = 0.3f), style = Stroke(width = 1.4.dp.toPx()))
    // Exhaust tips
    drawCircle(Color.Black.copy(alpha = 0.85f), 0.7f * s, Offset(-2.8f * s, 3.35f * s))
    drawCircle(Color.Black.copy(alpha = 0.85f), 0.7f * s, Offset(2.8f * s, 3.35f * s))
    // Wheels
    drawCircle(Color.Black.copy(alpha = 0.9f), 1.7f * s, Offset(-5.0f * s, 3.4f * s))
    drawCircle(Color.Black.copy(alpha = 0.9f), 1.7f * s, Offset(5.0f * s, 3.4f * s))
}
