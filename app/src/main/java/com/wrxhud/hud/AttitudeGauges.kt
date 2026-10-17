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
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ring colors matching the inclinometer reference (lime upper / cyan lower). */
private val RingGreen = Color(0xFFB8F000)
private val RingCyan = Color(0xFF00E5FF)
private val CarGlow = Color(0xFFE8FF4A)
private val HorizonLine = Color(0xFF94A3B8)

/**
 * Pitch / roll attitude — text strip, dual WRX rings, or one combined ring.
 * Yaw is not shown.
 */
@Composable
fun MotionAttitudeDisplay(
    motion: MotionReading,
    mode: MotionDisplayMode,
    modifier: Modifier = Modifier
) {
    when (mode) {
        MotionDisplayMode.TEXT -> MotionTelemetryStrip(motion = motion, modifier = modifier)
        MotionDisplayMode.RINGS -> DualAttitudeRings(motion = motion, modifier = modifier)
        MotionDisplayMode.COMBINED -> CombinedAttitudeRing(motion = motion, modifier = modifier)
    }
}

@Composable
fun MotionTelemetryStrip(
    motion: MotionReading,
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
        MotionCell("PIT", formatSignedDegrees(motion.pitchDeg))
        MotionCell("ROL", formatSignedDegrees(motion.rollDeg))
        MotionCell("G", formatGForce(motion.gForce))
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
        InclinometerGauge(
            label = "Pitch",
            degrees = motion.pitchDeg,
            kind = InclinometerKind.PITCH,
            size = 88.dp
        )
        InclinometerGauge(
            label = "Roll",
            degrees = motion.rollDeg,
            kind = InclinometerKind.ROLL,
            size = 88.dp
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
        ) {
            Text("G", color = HudColors.TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(
                formatGForce(motion.gForce),
                color = HudColors.TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CombinedAttitudeRing(
    motion: MotionReading,
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
        InclinometerGauge(
            label = "Attitude",
            degrees = motion.pitchDeg,
            secondaryDegrees = motion.rollDeg,
            kind = InclinometerKind.COMBINED,
            size = 96.dp
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            TinyMetric("Pitch", formatSignedDegrees(motion.pitchDeg))
            TinyMetric("Roll", formatSignedDegrees(motion.rollDeg))
            TinyMetric("G", formatGForce(motion.gForce))
        }
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
    label: String,
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Match other HUD widgets: short label above the gauge.
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
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
                fontSize = if (kind == InclinometerKind.COMBINED) 12.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.16f)
            )
        }
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
    translate(cx, cy + r * 0.10f) {
        when (kind) {
            InclinometerKind.PITCH -> {
                // Screen-remapped pitch: positive = nose up → clockwise for side-view nose-right.
                val tilt = pitchDeg.coerceIn(-45f, 45f)
                rotate(degrees = tilt) {
                    drawWrxVbSide(scale = r * 0.034f)
                }
            }
            InclinometerKind.ROLL -> {
                // Screen-remapped roll: positive = lean right → clockwise for rear view.
                val tilt = rollDeg.coerceIn(-45f, 45f)
                rotate(degrees = tilt) {
                    drawWrxVbRear(scale = r * 0.034f)
                }
            }
            InclinometerKind.COMBINED -> {
                val p = pitchDeg.coerceIn(-35f, 35f)
                val ro = rollDeg.coerceIn(-35f, 35f)
                rotate(degrees = ro) {
                    val foreshorten = (1f - p / 100f).coerceIn(0.65f, 1.15f)
                    scale(scaleX = 1f, scaleY = foreshorten) {
                        drawWrxVbRear(scale = r * 0.028f)
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
