package com.wrxhud.hud

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Liquid-water RPM fill with animated meniscus — keeps zone colors. */
fun DrawScope.drawTachLiquid(
    displayRpm: Float,
    maxRpm: Float,
    blueEnd: Float,
    greenEnd: Float,
    amberStart: Float,
    redStart: Float,
    redline: Float,
    phase: Float,
    trackColor: Color,
    blue: Color,
    green: Color,
    amber: Color,
    red: Color,
    accent: Color
) {
    val h = size.height * 0.78f
    val top = (size.height - h) / 2f
    val w = size.width
    val fraction = min(displayRpm / maxRpm, 1f)
    val fillW = w * fraction
    val corner = CornerRadius(10f, 10f)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, top),
        size = Size(w, h),
        cornerRadius = corner
    )

    // Glass wall highlight
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            startY = top,
            endY = top + h * 0.4f
        ),
        topLeft = Offset(0f, top),
        size = Size(w, h * 0.4f),
        cornerRadius = corner
    )

    if (fillW <= 1f) {
        drawRedline(redline / maxRpm * w, top, h, accent)
        return
    }

    val waveAmp = h * 0.07f
    val liquid = Path().apply {
        moveTo(0f, top + h)
        lineTo(0f, top + waveY(0f, fillW, phase, waveAmp, h * 0.12f))
        val steps = 28
        for (i in 1..steps) {
            val x = fillW * i / steps
            lineTo(x, top + waveY(x, fillW, phase, waveAmp, h * 0.12f))
        }
        lineTo(fillW, top + h)
        close()
    }

    val stops = tachColorStops(blue, green, amber, red, blueEnd, greenEnd, amberStart, redStart, redline, maxRpm)
    drawPath(
        liquid,
        brush = Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = w)
    )
    // Surface shimmer
    drawPath(
        liquid,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
            startY = top,
            endY = top + h * 0.45f
        )
    )
    // Meniscus edge
    val edge = Path().apply {
        val y0 = top + waveY(0f, fillW, phase, waveAmp, h * 0.12f)
        moveTo(0f, y0)
        val steps = 28
        for (i in 1..steps) {
            val x = fillW * i / steps
            lineTo(x, top + waveY(x, fillW, phase, waveAmp, h * 0.12f))
        }
    }
    drawPath(
        edge,
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
    )

    drawRedline(redline / maxRpm * w, top, h, accent)
}

/** Dual helix: RPM (zone colors) + boost (blue→white) twisted across the bar. */
fun DrawScope.drawTachHelix(
    rpmFraction: Float,
    boostFraction: Float,
    phase: Float,
    trackColor: Color,
    blue: Color,
    green: Color,
    amber: Color,
    red: Color,
    accent: Color,
    boostBlue: Color
) {
    val h = size.height
    val w = size.width
    val midY = h / 2f
    val amp = h * 0.32f
    val turns = 2.4f
    val steps = 64

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, h * 0.08f),
        size = Size(w, h * 0.84f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    fun strandY(t: Float, strand: Int): Float {
        val angle = t * turns * 2f * PI.toFloat() + phase + strand * PI.toFloat()
        return midY + sin(angle) * amp
    }

    fun rpmColorAt(t: Float): Color = when {
        t >= 0.92f -> red
        t >= 0.78f -> amber
        t >= 0.45f -> green
        else -> blue
    }

    fun boostColorAt(t: Float): Color {
        val u = t.coerceIn(0f, 1f)
        return lerpColor(boostBlue, Color.White, 0.25f + 0.65f * u)
    }

    // Draw both strands as thick polylines with progress fill.
    for (strand in 0..1) {
        val progress = if (strand == 0) rpmFraction else boostFraction
        for (i in 0 until steps) {
            val t0 = i / steps.toFloat()
            val t1 = (i + 1) / steps.toFloat()
            val lit = t1 <= progress.coerceIn(0f, 1f)
            val x0 = w * t0
            val x1 = w * t1
            val y0 = strandY(t0, strand)
            val y1 = strandY(t1, strand)
            val color = if (!lit) {
                Color.Black.copy(alpha = 0.35f)
            } else if (strand == 0) {
                rpmColorAt(t0)
            } else {
                boostColorAt(t0)
            }
            drawLine(
                color = color,
                start = Offset(x0, y0),
                end = Offset(x1, y1),
                strokeWidth = if (strand == 0) 7.dp.toPx() else 5.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            if (lit) {
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = if (strand == 0) 4.dp.toPx() else 3.dp.toPx(),
                    center = Offset(x1, y1)
                )
            }
        }
    }

    // Center spine
    drawLine(
        color = accent.copy(alpha = 0.35f),
        start = Offset(0f, midY),
        end = Offset(w, midY),
        strokeWidth = 1.dp.toPx()
    )
}

fun DrawScope.drawLampLiquid(
    lit: Boolean,
    faceColor: Color,
    lampOff: Color,
    phase: Float
) {
    val radius = CornerRadius(size.height * 0.45f, size.height * 0.45f)
    drawRoundRect(
        color = if (lit) faceColor.copy(alpha = 0.22f) else lampOff,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = radius
    )
    if (!lit) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, size.height * 0.4f),
            cornerRadius = radius
        )
        return
    }

    val fillTop = size.height * (0.18f + 0.06f * sin(phase * 2f * PI.toFloat()).toFloat())
    val liquid = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, fillTop)
        val steps = 12
        for (i in 1..steps) {
            val x = size.width * i / steps
            val y = fillTop + sin(phase * 2 * PI + i * 0.7).toFloat() * size.height * 0.06f
            lineTo(x, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(liquid, color = faceColor)
    drawPath(
        liquid,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
            startY = fillTop,
            endY = size.height * 0.55f
        )
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
        ),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, size.height * 0.35f),
        cornerRadius = radius
    )
}

/** Deeper extrusion + stronger specular for BAR_3D. */
fun DrawScope.drawTach3dEnhanced(
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
    val frontTop = top + depth * 0.12f
    val frontBottom = frontTop + trackHeight

    val floor = Path().apply {
        moveTo(0f, frontBottom)
        lineTo(trackWidth, frontBottom)
        lineTo(trackWidth + depth, frontBottom + depth)
        lineTo(depth, frontBottom + depth)
        close()
    }
    drawPath(
        floor,
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.35f)),
            startY = frontBottom,
            endY = frontBottom + depth
        )
    )

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
            colors = listOf(Color.White.copy(alpha = 0.22f), border, trackColor),
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
    drawPath(
        endCap,
        brush = Brush.horizontalGradient(
            listOf(panel, Color.Black.copy(alpha = 0.55f)),
            startX = trackWidth,
            endX = trackWidth + depth
        )
    )

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, frontTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(7f, 7f)
    )
    drawRect(color = blue.copy(alpha = 0.14f), topLeft = Offset(0f, frontTop), size = Size(blueX, trackHeight))
    drawRect(color = green.copy(alpha = 0.14f), topLeft = Offset(blueX, frontTop), size = Size(greenX - blueX, trackHeight))
    drawRect(color = amber.copy(alpha = 0.14f), topLeft = Offset(amberX, frontTop), size = Size(redX - amberX, trackHeight))
    drawRect(color = red.copy(alpha = 0.18f), topLeft = Offset(redX, frontTop), size = Size(trackWidth - redX, trackHeight))

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
                colorStops = tachStops.map { (t, c) -> t to c.copy(alpha = 0.9f) }.toTypedArray(),
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
                listOf(Color.White.copy(alpha = 0.35f), tipColor, tipColor.copy(alpha = 0.45f)),
                startX = fillWidth,
                endX = fillWidth + depth
            )
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(colorStops = tachStops, startX = 0f, endX = trackWidth),
            topLeft = Offset(0f, frontTop),
            size = Size(fillWidth, trackHeight),
            cornerRadius = CornerRadius(7f, 7f)
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.25f)),
                startY = frontTop,
                endY = frontBottom
            ),
            topLeft = Offset(0f, frontTop),
            size = Size(fillWidth, trackHeight)
        )
    }

    listOf(blueX, greenX, amberX, redX).forEach { x ->
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, frontTop), Offset(x, frontBottom), 2f)
    }
    drawLine(accent, Offset(redlineX, frontTop - 4f), Offset(redlineX, frontBottom + 4f), 3.5f)
    drawLine(
        accent.copy(alpha = 0.5f),
        Offset(redlineX, frontTop - 4f),
        Offset(redlineX + depth * 0.4f, frontTop - 4f + depth * 0.4f),
        2.5f
    )
}

fun DrawScope.drawLamp3dEnhanced(
    lit: Boolean,
    faceColor: Color,
    lampOff: Color,
    border: Color,
    panel: Color
) {
    val depth = size.height * 0.38f
    val frontH = size.height - depth
    val frontW = size.width - depth * 0.65f
    val frontTop = depth * 0.1f
    val radius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())

    val floor = Path().apply {
        moveTo(0f, frontTop + frontH)
        lineTo(frontW, frontTop + frontH)
        lineTo(frontW + depth * 0.65f, frontTop + frontH + depth)
        lineTo(depth * 0.65f, frontTop + frontH + depth)
        close()
    }
    drawPath(floor, color = Color.Black.copy(alpha = if (lit) 0.55f else 0.7f))

    val roof = Path().apply {
        moveTo(0f, frontTop)
        lineTo(frontW, frontTop)
        lineTo(frontW + depth * 0.65f, frontTop + depth)
        lineTo(depth * 0.65f, frontTop + depth)
        close()
    }
    drawPath(
        roof,
        brush = Brush.verticalGradient(
            if (lit) listOf(Color.White.copy(alpha = 0.55f), faceColor, faceColor.copy(alpha = 0.5f))
            else listOf(border, lampOff),
            startY = frontTop,
            endY = frontTop + depth
        )
    )

    val endCap = Path().apply {
        moveTo(frontW, frontTop)
        lineTo(frontW + depth * 0.65f, frontTop + depth)
        lineTo(frontW + depth * 0.65f, frontTop + frontH + depth)
        lineTo(frontW, frontTop + frontH)
        close()
    }
    drawPath(endCap, color = if (lit) faceColor.copy(alpha = 0.5f) else panel)

    drawRoundRect(
        color = faceColor,
        topLeft = Offset(0f, frontTop),
        size = Size(frontW, frontH),
        cornerRadius = radius
    )
    if (lit) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.65f), Color.Transparent)
            ),
            topLeft = Offset(0f, frontTop),
            size = Size(frontW, frontH * 0.5f),
            cornerRadius = radius
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = min(frontW, frontH) * 0.18f,
            center = Offset(frontW * 0.28f, frontTop + frontH * 0.32f)
        )
    } else {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
            ),
            topLeft = Offset(0f, frontTop),
            size = Size(frontW, frontH * 0.45f),
            cornerRadius = radius
        )
    }
}

private fun waveY(x: Float, fillW: Float, phase: Float, amp: Float, base: Float): Float {
    val t = if (fillW <= 0f) 0f else x / fillW
    return base + sin((t * 3.2f + phase) * 2f * PI).toFloat() * amp +
        sin((t * 7.1f - phase * 1.3f) * 2f * PI).toFloat() * amp * 0.35f
}

private fun DrawScope.drawRedline(x: Float, top: Float, h: Float, accent: Color) {
    drawLine(accent, Offset(x, top - 3f), Offset(x, top + h + 3f), 3f)
}

private fun tachColorStops(
    blue: Color,
    green: Color,
    amber: Color,
    red: Color,
    blueEnd: Float,
    greenEnd: Float,
    amberStart: Float,
    redStart: Float,
    redline: Float,
    maxRpm: Float
): Array<Pair<Float, Color>> = arrayOf(
    0f to blue,
    (blueEnd / maxRpm) to blue,
    (greenEnd / maxRpm) to green,
    (amberStart / maxRpm) to green,
    (redStart / maxRpm) to amber,
    (redline / maxRpm) to red,
    1f to red
)

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * u,
        green = a.green + (b.green - a.green) * u,
        blue = a.blue + (b.blue - a.blue) * u,
        alpha = a.alpha + (b.alpha - a.alpha) * u
    )
}
