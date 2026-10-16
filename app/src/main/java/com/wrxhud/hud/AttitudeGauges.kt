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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pitch / roll / yaw / g — text strip, three circular car gauges, or one combined attitude ring.
 */
@Composable
fun MotionAttitudeDisplay(
    motion: MotionReading,
    mode: MotionDisplayMode,
    modifier: Modifier = Modifier
) {
    when (mode) {
        MotionDisplayMode.TEXT -> MotionTelemetryStrip(motion = motion, modifier = modifier)
        MotionDisplayMode.TRIPLE -> TripleAttitudeRings(motion = motion, modifier = modifier)
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
        MotionCell("YAW", formatSignedDegrees(motion.yawDeg))
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
private fun TripleAttitudeRings(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AxisAttitudeGauge(
            label = "PIT",
            valueText = formatSignedDegrees(motion.pitchDeg),
            axis = AttitudeAxis.PITCH,
            pitch = motion.pitchDeg ?: 0f,
            roll = 0f,
            yaw = 0f,
            size = 56.dp
        )
        AxisAttitudeGauge(
            label = "ROL",
            valueText = formatSignedDegrees(motion.rollDeg),
            axis = AttitudeAxis.ROLL,
            pitch = 0f,
            roll = motion.rollDeg ?: 0f,
            yaw = 0f,
            size = 56.dp
        )
        AxisAttitudeGauge(
            label = "YAW",
            valueText = formatSignedDegrees(motion.yawDeg),
            axis = AttitudeAxis.YAW,
            pitch = 0f,
            roll = 0f,
            yaw = motion.yawDeg ?: 0f,
            size = 56.dp
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 2.dp)
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        AxisAttitudeGauge(
            label = "ATT",
            valueText = "P${formatSignedDegrees(motion.pitchDeg)} R${formatSignedDegrees(motion.rollDeg)}",
            axis = AttitudeAxis.COMBINED,
            pitch = motion.pitchDeg ?: 0f,
            roll = motion.rollDeg ?: 0f,
            yaw = motion.yawDeg ?: 0f,
            size = 72.dp,
            valueFontSize = 9
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TinyMetric("PIT", formatSignedDegrees(motion.pitchDeg))
            TinyMetric("ROL", formatSignedDegrees(motion.rollDeg))
            TinyMetric("YAW", formatSignedDegrees(motion.yawDeg))
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

private enum class AttitudeAxis { PITCH, ROLL, YAW, COMBINED }

@Composable
private fun AxisAttitudeGauge(
    label: String,
    valueText: String,
    axis: AttitudeAxis,
    pitch: Float,
    roll: Float,
    yaw: Float,
    size: Dp,
    valueFontSize: Int = 10
) {
    val border = HudColors.Border
    val accent = HudColors.Accent
    val panel = HudColors.Panel
    val carBody = HudColors.TextPrimary
    val carDim = HudColors.TextDim
    val blue = HudColors.BoostBlue

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawAttitudeRing(
                    axis = axis,
                    pitch = pitch,
                    roll = roll,
                    yaw = yaw,
                    border = border,
                    accent = accent,
                    panel = panel,
                    carBody = carBody,
                    carDim = carDim,
                    blue = blue
                )
            }
        }
        Text(
            text = label,
            color = HudColors.TextDim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = valueText,
            color = HudColors.TextPrimary,
            fontSize = valueFontSize.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun DrawScope.drawAttitudeRing(
    axis: AttitudeAxis,
    pitch: Float,
    roll: Float,
    yaw: Float,
    border: Color,
    accent: Color,
    panel: Color,
    carBody: Color,
    carDim: Color,
    blue: Color
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f - 2f

    drawCircle(color = panel, radius = r, center = Offset(cx, cy))
    drawCircle(
        color = border,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )
    // Horizon / reference ticks
    for (i in 0 until 12) {
        val a = Math.toRadians(i * 30.0)
        val inner = r * 0.82f
        val outer = r * 0.94f
        drawLine(
            color = if (i % 3 == 0) accent.copy(alpha = 0.7f) else border,
            start = Offset(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat()),
            end = Offset(cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat()),
            strokeWidth = if (i % 3 == 0) 2f else 1.2f
        )
    }
    // Horizon line for pitch/roll context
    drawLine(
        color = blue.copy(alpha = 0.35f),
        start = Offset(cx - r * 0.7f, cy),
        end = Offset(cx + r * 0.7f, cy),
        strokeWidth = 1.2f
    )

    translate(cx, cy) {
        when (axis) {
            AttitudeAxis.PITCH -> {
                // Side view: pitch rotates nose up/down
                val tilt = pitch.coerceIn(-45f, 45f)
                rotate(degrees = -tilt) {
                    drawCarSide(carBody, carDim, accent, scale = r * 0.028f)
                }
            }
            AttitudeAxis.ROLL -> {
                val tilt = roll.coerceIn(-45f, 45f)
                rotate(degrees = tilt) {
                    drawCarRear(carBody, carDim, accent, scale = r * 0.028f)
                }
            }
            AttitudeAxis.YAW -> {
                rotate(degrees = -yaw) {
                    drawCarTop(carBody, carDim, accent, scale = r * 0.026f)
                }
            }
            AttitudeAxis.COMBINED -> {
                val p = pitch.coerceIn(-35f, 35f)
                val ro = roll.coerceIn(-35f, 35f)
                rotate(degrees = -yaw) {
                    rotate(degrees = ro) {
                        // Pitch foreshortens the top view (nose up → shorter silhouette)
                        val foreshorten = 1f - (p / 90f).coerceIn(-0.35f, 0.35f)
                        scale(scaleX = 1f, scaleY = foreshorten.coerceIn(0.55f, 1.2f)) {
                            drawCarTop(carBody, carDim, accent, scale = r * 0.024f)
                        }
                        // Pitch cue: nose lift marker
                        val noseLift = (-p / 35f) * r * 0.12f
                        drawCircle(
                            color = accent,
                            radius = 3.dp.toPx(),
                            center = Offset(0f, -r * 0.22f + noseLift)
                        )
                    }
                }
            }
        }
    }
}

/** Top-down WRX-ish silhouette (nose toward -Y). */
private fun DrawScope.drawCarTop(body: Color, dim: Color, accent: Color, scale: Float) {
    val s = scale
    val path = Path().apply {
        moveTo(0f, -9f * s)
        lineTo(3.2f * s, -6.5f * s)
        lineTo(4.2f * s, -1f * s)
        lineTo(4f * s, 6f * s)
        lineTo(2.8f * s, 9f * s)
        lineTo(-2.8f * s, 9f * s)
        lineTo(-4f * s, 6f * s)
        lineTo(-4.2f * s, -1f * s)
        lineTo(-3.2f * s, -6.5f * s)
        close()
    }
    drawPath(path, color = body.copy(alpha = 0.92f))
    // Cabin
    drawRoundRect(
        color = dim.copy(alpha = 0.85f),
        topLeft = Offset(-2.2f * s, -2.5f * s),
        size = Size(4.4f * s, 5.5f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2f * s, 1.2f * s)
    )
    // Nose highlight
    drawCircle(color = accent, radius = 1.4f * s, center = Offset(0f, -7.2f * s))
    // Wheels
    listOf(
        Offset(-3.6f * s, -4f * s),
        Offset(3.6f * s, -4f * s),
        Offset(-3.6f * s, 5f * s),
        Offset(3.6f * s, 5f * s)
    ).forEach { drawCircle(color = Color.Black.copy(alpha = 0.75f), radius = 1.1f * s, center = it) }
}

/** Side view (nose toward +X). */
private fun DrawScope.drawCarSide(body: Color, dim: Color, accent: Color, scale: Float) {
    val s = scale
    val bodyPath = Path().apply {
        moveTo(-8f * s, 1.5f * s)
        lineTo(-6f * s, -1.2f * s)
        lineTo(-1f * s, -2.2f * s)
        lineTo(2.5f * s, -2f * s)
        lineTo(5f * s, -0.6f * s)
        lineTo(8f * s, 0.4f * s)
        lineTo(8f * s, 2.2f * s)
        lineTo(-8f * s, 2.2f * s)
        close()
    }
    drawPath(bodyPath, color = body.copy(alpha = 0.92f))
    // Cabin glass
    drawPath(
        Path().apply {
            moveTo(-2.5f * s, -0.3f * s)
            lineTo(-0.5f * s, -2f * s)
            lineTo(2.2f * s, -1.8f * s)
            lineTo(3.5f * s, -0.2f * s)
            close()
        },
        color = dim.copy(alpha = 0.9f)
    )
    drawCircle(color = accent, radius = 1.1f * s, center = Offset(7f * s, 0.6f * s))
    drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = 1.6f * s, center = Offset(-4.5f * s, 2.6f * s))
    drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = 1.6f * s, center = Offset(4.2f * s, 2.6f * s))
}

/** Rear view for roll. */
private fun DrawScope.drawCarRear(body: Color, dim: Color, accent: Color, scale: Float) {
    val s = scale
    val bodyPath = Path().apply {
        moveTo(-6f * s, 1.5f * s)
        lineTo(-5f * s, -1.5f * s)
        lineTo(-2f * s, -3f * s)
        lineTo(2f * s, -3f * s)
        lineTo(5f * s, -1.5f * s)
        lineTo(6f * s, 1.5f * s)
        lineTo(4.5f * s, 3f * s)
        lineTo(-4.5f * s, 3f * s)
        close()
    }
    drawPath(bodyPath, color = body.copy(alpha = 0.92f))
    drawRoundRect(
        color = dim.copy(alpha = 0.9f),
        topLeft = Offset(-2.8f * s, -2.2f * s),
        size = Size(5.6f * s, 2.4f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f * s, 0.8f * s)
    )
    // Spoiler
    drawLine(
        color = accent,
        start = Offset(-3.5f * s, -3.2f * s),
        end = Offset(3.5f * s, -3.2f * s),
        strokeWidth = 2f * s.coerceAtLeast(1f)
    )
    drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = 1.5f * s, center = Offset(-4.2f * s, 3.2f * s))
    drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = 1.5f * s, center = Offset(4.2f * s, 3.2f * s))
}
