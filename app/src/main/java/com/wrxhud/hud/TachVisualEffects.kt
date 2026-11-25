package com.wrxhud.hud

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 * Draw-phase renderers for the tach bar styles and shift lamps.
 *
 * Everything that depends only on size + theme colors (Brush, Path, Stroke, gradient
 * color lists) is built once in the constructor, i.e. inside Modifier.drawWithCache, and
 * only re-built when the size or one of those inputs changes. The per-frame `draw` calls
 * only issue draw ops (and rewind/refill a reused Path where the geometry moves).
 */

/** Theme colors the tach renderers need; data class so it works as a cache key. */
internal data class TachPalette(
    val trackColor: Color,
    val blue: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val accent: Color,
    val panel: Color,
    val border: Color,
    val boostBlue: Color,
    val silver: Color
)

/** Liquid-water RPM fill with animated meniscus — keeps zone colors. */
internal class LiquidTachRenderer(
    density: Density,
    size: Size,
    private val p: TachPalette,
    private val redline: Float,
    private val maxRpm: Float,
    blueEnd: Float,
    greenEnd: Float,
    amberStart: Float,
    redStart: Float
) {
    private val w = size.width
    private val h = size.height * 0.78f
    private val top = (size.height - h) / 2f
    private val corner = CornerRadius(10f, 10f)
    private val glassBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
        startY = top,
        endY = top + h * 0.4f
    )
    private val fillBrush = Brush.horizontalGradient(
        colorStops = tachColorStops(p.blue, p.green, p.amber, p.red, blueEnd, greenEnd, amberStart, redStart, redline, maxRpm),
        startX = 0f,
        endX = w
    )
    private val shimmerBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
        startY = top,
        endY = top + h * 0.45f
    )
    private val edgeColor = Color.White.copy(alpha = 0.55f)
    private val edgeStroke = Stroke(width = with(density) { 2.2.dp.toPx() }, cap = StrokeCap.Round)
    private val redlineX = redline / maxRpm * w
    private val liquid = Path()
    private val edge = Path()

    fun draw(scope: DrawScope, displayRpm: Float, phase: Float) = with(scope) {
        val fraction = min(displayRpm / maxRpm, 1f)
        val fillW = w * fraction

        drawRoundRect(color = p.trackColor, topLeft = Offset(0f, top), size = Size(w, h), cornerRadius = corner)
        // Glass wall highlight
        drawRoundRect(
            brush = glassBrush,
            topLeft = Offset(0f, top),
            size = Size(w, h * 0.4f),
            cornerRadius = corner
        )
        if (fillW <= 1f) {
            drawRedline(redlineX, top, h, p.accent)
            return@with
        }

        val waveAmp = h * 0.07f
        val waveBase = h * 0.12f
        liquid.rewind()
        liquid.moveTo(0f, top + h)
        liquid.lineTo(0f, top + waveY(0f, fillW, phase, waveAmp, waveBase))
        val steps = 28
        for (i in 1..steps) {
            val x = fillW * i / steps
            liquid.lineTo(x, top + waveY(x, fillW, phase, waveAmp, waveBase))
        }
        liquid.lineTo(fillW, top + h)
        liquid.close()

        drawPath(liquid, brush = fillBrush)
        // Surface shimmer
        drawPath(liquid, brush = shimmerBrush)
        // Meniscus edge
        edge.rewind()
        edge.moveTo(0f, top + waveY(0f, fillW, phase, waveAmp, waveBase))
        for (i in 1..steps) {
            val x = fillW * i / steps
            edge.lineTo(x, top + waveY(x, fillW, phase, waveAmp, waveBase))
        }
        drawPath(edge, color = edgeColor, style = edgeStroke)

        drawRedline(redlineX, top, h, p.accent)
    }
}

/**
 * Deeper extrusion + stronger specular for BAR_3D. Zone positions are given as fractions
 * of the bar (0..1); the same renderer draws the RPM bar and the throttle bar.
 */
internal class Tach3dRenderer(
    size: Size,
    private val p: TachPalette,
    blueF: Float,
    greenF: Float,
    amberF: Float,
    redF: Float,
    redlineF: Float,
    stops: Array<Pair<Float, Color>>
) {
    private val depth = size.height * 0.28f
    private val trackHeight = size.height * 0.52f
    private val top = (size.height - trackHeight - depth) / 2f
    private val trackWidth = size.width - depth
    private val blueX = trackWidth * blueF
    private val greenX = trackWidth * greenF
    private val amberX = trackWidth * amberF
    private val redX = trackWidth * redF
    private val redlineX = trackWidth * redlineF
    private val frontTop = top + depth * 0.12f
    private val frontBottom = frontTop + trackHeight
    private val corner = CornerRadius(7f, 7f)

    private val floor = Path().apply {
        moveTo(0f, frontBottom)
        lineTo(trackWidth, frontBottom)
        lineTo(trackWidth + depth, frontBottom + depth)
        lineTo(depth, frontBottom + depth)
        close()
    }
    private val floorBrush = Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.35f)),
        startY = frontBottom,
        endY = frontBottom + depth
    )
    private val roof = Path().apply {
        moveTo(0f, frontTop)
        lineTo(trackWidth, frontTop)
        lineTo(trackWidth + depth, frontTop + depth)
        lineTo(depth, frontTop + depth)
        close()
    }
    private val roofBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.22f), p.border, p.trackColor),
        startY = frontTop,
        endY = frontTop + depth
    )
    private val endCap = Path().apply {
        moveTo(trackWidth, frontTop)
        lineTo(trackWidth + depth, frontTop + depth)
        lineTo(trackWidth + depth, frontBottom + depth)
        lineTo(trackWidth, frontBottom)
        close()
    }
    private val endCapBrush = Brush.horizontalGradient(
        listOf(p.panel, Color.Black.copy(alpha = 0.55f)),
        startX = trackWidth,
        endX = trackWidth + depth
    )
    private val blueZone = p.blue.copy(alpha = 0.14f)
    private val greenZone = p.green.copy(alpha = 0.14f)
    private val amberZone = p.amber.copy(alpha = 0.14f)
    private val redZone = p.red.copy(alpha = 0.18f)

    private val fillRoof = Path()
    private val fillRoofBrush = Brush.horizontalGradient(
        colorStops = Array(stops.size) { i -> stops[i].first to stops[i].second.copy(alpha = 0.9f) },
        startX = 0f,
        endX = trackWidth
    )

    // Tip cap is built at x = 0 and translated to the fill edge, so one Path/Brush set is
    // shared by all frames. Index: 0 blue, 1 green, 2 amber, 3 red.
    private val tip = Path().apply {
        moveTo(0f, frontTop)
        lineTo(depth, frontTop + depth)
        lineTo(depth, frontBottom + depth)
        lineTo(0f, frontBottom)
        close()
    }
    private val tipBrushes = Array(4) { idx ->
        val tipColor = when (idx) {
            3 -> p.red
            2 -> p.amber
            1 -> p.green
            else -> p.blue
        }
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.35f), tipColor, tipColor.copy(alpha = 0.45f)),
            startX = 0f,
            endX = depth
        )
    }
    private val fillBrush = Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = trackWidth)
    private val sheenBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.25f)),
        startY = frontTop,
        endY = frontBottom
    )
    private val dividerColor = Color.Black.copy(alpha = 0.35f)
    private val redlineShadow = p.accent.copy(alpha = 0.5f)

    fun draw(scope: DrawScope, fraction: Float) = with(scope) {
        val fillWidth = trackWidth * fraction
        drawPath(floor, brush = floorBrush)
        drawPath(roof, brush = roofBrush)
        drawPath(endCap, brush = endCapBrush)

        drawRoundRect(color = p.trackColor, topLeft = Offset(0f, frontTop), size = Size(trackWidth, trackHeight), cornerRadius = corner)
        drawRect(color = blueZone, topLeft = Offset(0f, frontTop), size = Size(blueX, trackHeight))
        drawRect(color = greenZone, topLeft = Offset(blueX, frontTop), size = Size(greenX - blueX, trackHeight))
        drawRect(color = amberZone, topLeft = Offset(amberX, frontTop), size = Size(redX - amberX, trackHeight))
        drawRect(color = redZone, topLeft = Offset(redX, frontTop), size = Size(trackWidth - redX, trackHeight))

        if (fillWidth > 0f) {
            fillRoof.rewind()
            fillRoof.moveTo(0f, frontTop)
            fillRoof.lineTo(fillWidth, frontTop)
            fillRoof.lineTo(fillWidth + depth, frontTop + depth)
            fillRoof.lineTo(depth, frontTop + depth)
            fillRoof.close()
            drawPath(fillRoof, brush = fillRoofBrush)

            val tipIdx = when {
                fillWidth >= redX -> 3
                fillWidth >= amberX -> 2
                fillWidth >= greenX -> 1
                else -> 0
            }
            translate(left = fillWidth, top = 0f) {
                drawPath(tip, brush = tipBrushes[tipIdx])
            }

            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(0f, frontTop),
                size = Size(fillWidth, trackHeight),
                cornerRadius = corner
            )
            drawRect(brush = sheenBrush, topLeft = Offset(0f, frontTop), size = Size(fillWidth, trackHeight))
        }

        drawLine(dividerColor, Offset(blueX, frontTop), Offset(blueX, frontBottom), 2f)
        drawLine(dividerColor, Offset(greenX, frontTop), Offset(greenX, frontBottom), 2f)
        drawLine(dividerColor, Offset(amberX, frontTop), Offset(amberX, frontBottom), 2f)
        drawLine(dividerColor, Offset(redX, frontTop), Offset(redX, frontBottom), 2f)
        drawLine(p.accent, Offset(redlineX, frontTop - 4f), Offset(redlineX, frontBottom + 4f), 3.5f)
        drawLine(
            redlineShadow,
            Offset(redlineX, frontTop - 4f),
            Offset(redlineX + depth * 0.4f, frontTop - 4f + depth * 0.4f),
            2.5f
        )
    }
}

/**
 * Audi R8 race-screen style: dense vertical segments, silver fill, permanent
 * green → amber → red shift-zone blocks near redline (bright when lit).
 */
internal class RaceSegTachRenderer(
    size: Size,
    private val p: TachPalette,
    private val redline: Float,
    private val maxRpm: Float,
    private val greenZoneStart: Float,
    private val amberZoneStart: Float,
    private val redZoneStart: Float
) {
    private val h = size.height * 0.72f
    private val top = (size.height - h) / 2f
    private val w = size.width
    private val segCount = 52
    private val gap = 2.2f
    private val segW = ((w - gap * (segCount - 1)) / segCount).coerceAtLeast(2f)
    private val corner = CornerRadius(1.5f, 1.5f)
    private val trackCorner = CornerRadius(4f, 4f)
    private val offFace = Color(0xFF2A2E34).copy(alpha = 0.85f)
    private val dimRed = p.red.copy(alpha = 0.35f)
    private val tipGlow = Color.White.copy(alpha = 0.45f)
    private val redlineX = (redline / maxRpm).coerceIn(0f, 1f) * w

    // One brush for every lit segment: gradient spans the canvas height like the original.
    private val litSheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))

    fun draw(scope: DrawScope, displayRpm: Float) = with(scope) {
        val litCount = ((displayRpm / maxRpm).coerceIn(0f, 1f) * segCount).toInt()

        // Track plate
        drawRoundRect(
            color = p.trackColor,
            topLeft = Offset(0f, top - 2f),
            size = Size(w, h + 4f),
            cornerRadius = trackCorner
        )

        for (i in 0 until segCount) {
            val x = i * (segW + gap)
            val rpmAt = (i + 0.5f) / segCount * maxRpm
            val lit = i < litCount
            val zoneColor = when {
                rpmAt >= redZoneStart -> p.red
                rpmAt >= amberZoneStart -> p.amber
                rpmAt >= greenZoneStart -> p.green
                else -> p.silver
            }
            val face = when {
                // Permanent dim redline segments (R8 9–10 style)
                rpmAt >= redline && !lit -> dimRed
                lit && rpmAt >= greenZoneStart -> zoneColor
                lit -> p.silver
                else -> offFace
            }
            val segH = if (lit) h else h * 0.88f
            val segTop = top + (h - segH) / 2f
            drawRoundRect(
                color = face,
                topLeft = Offset(x, segTop),
                size = Size(segW, segH),
                cornerRadius = corner
            )
            if (lit) {
                drawRoundRect(
                    brush = litSheen,
                    topLeft = Offset(x, segTop),
                    size = Size(segW, segH * 0.4f),
                    cornerRadius = corner
                )
            }
        }

        // Tip glow at leading lit segment
        if (litCount in 1..segCount) {
            val tipX = (litCount - 1) * (segW + gap) + segW / 2f
            drawCircle(color = tipGlow, radius = h * 0.22f, center = Offset(tipX, top + h / 2f))
        }

        drawLine(p.accent, Offset(redlineX, top - 4f), Offset(redlineX, top + h + 4f), 2.5f)
    }
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

// ---------------------------------------------------------------------------------------
// Shift lamps. Each builder runs inside Modifier.drawWithCache (rebuilt when size or the
// lamp inputs change, e.g. a lamp lighting up) and returns the draw lambda.
// ---------------------------------------------------------------------------------------

private val LAMP_LIQUID_SURFACE_COLORS = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)

/**
 * Liquid lamp. [phase] is read inside the draw lambda. The surface gradient start depends
 * on the animated fill level, so one small Brush per lit lamp per frame is unavoidable.
 */
internal fun CacheDrawScope.lampLiquid(
    lit: Boolean,
    faceColor: Color,
    lampOff: Color,
    phase: () -> Float
): DrawResult {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(h * 0.45f, h * 0.45f)
    val bodyColor = if (lit) faceColor.copy(alpha = 0.22f) else lampOff
    val unlitShade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent))
    val topHighlight = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))
    val liquid = Path()
    return onDrawBehind {
        drawRoundRect(color = bodyColor, topLeft = Offset.Zero, size = size, cornerRadius = radius)
        if (!lit) {
            drawRoundRect(
                brush = unlitShade,
                topLeft = Offset.Zero,
                size = Size(w, h * 0.4f),
                cornerRadius = radius
            )
            return@onDrawBehind
        }
        val ph = phase()
        val fillTop = h * (0.18f + 0.06f * sin(ph * 2f * PI.toFloat()))
        liquid.rewind()
        liquid.moveTo(0f, h)
        liquid.lineTo(0f, fillTop)
        val steps = 12
        for (i in 1..steps) {
            val x = w * i / steps
            val y = fillTop + sin(ph * 2 * PI + i * 0.7).toFloat() * h * 0.06f
            liquid.lineTo(x, y)
        }
        liquid.lineTo(w, h)
        liquid.close()
        drawPath(liquid, color = faceColor)
        drawPath(
            liquid,
            brush = Brush.verticalGradient(
                LAMP_LIQUID_SURFACE_COLORS,
                startY = fillTop,
                endY = h * 0.55f
            )
        )
        drawRoundRect(
            brush = topHighlight,
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.35f),
            cornerRadius = radius
        )
    }
}

internal fun CacheDrawScope.lampRaceSegment(
    lit: Boolean,
    faceColor: Color,
    lampOff: Color
): DrawResult {
    val gap = 2.dp.toPx()
    val cols = 3
    val rows = 2
    val cellW = (size.width - gap * (cols - 1)) / cols
    val cellH = (size.height - gap * (rows - 1)) / rows
    val corner = CornerRadius(1.5f, 1.5f)
    val color = if (lit) faceColor else lampOff
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent))
    return onDrawBehind {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * (cellW + gap)
                val y = row * (cellH + gap)
                drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(cellW, cellH), cornerRadius = corner)
                if (lit) {
                    drawRoundRect(
                        brush = sheen,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH * 0.45f),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

internal fun CacheDrawScope.lamp3dEnhanced(
    lit: Boolean,
    faceColor: Color,
    lampOff: Color,
    border: Color,
    panel: Color
): DrawResult {
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
    val floorColor = Color.Black.copy(alpha = if (lit) 0.55f else 0.7f)
    val roof = Path().apply {
        moveTo(0f, frontTop)
        lineTo(frontW, frontTop)
        lineTo(frontW + depth * 0.65f, frontTop + depth)
        lineTo(depth * 0.65f, frontTop + depth)
        close()
    }
    val roofBrush = Brush.verticalGradient(
        if (lit) listOf(Color.White.copy(alpha = 0.55f), faceColor, faceColor.copy(alpha = 0.5f))
        else listOf(border, lampOff),
        startY = frontTop,
        endY = frontTop + depth
    )
    val endCap = Path().apply {
        moveTo(frontW, frontTop)
        lineTo(frontW + depth * 0.65f, frontTop + depth)
        lineTo(frontW + depth * 0.65f, frontTop + frontH + depth)
        lineTo(frontW, frontTop + frontH)
        close()
    }
    val endCapColor = if (lit) faceColor.copy(alpha = 0.5f) else panel
    val litSheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.Transparent))
    val unlitShade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))
    val glint = Color.White.copy(alpha = 0.35f)
    val glintR = min(frontW, frontH) * 0.18f
    return onDrawBehind {
        drawPath(floor, color = floorColor)
        drawPath(roof, brush = roofBrush)
        drawPath(endCap, color = endCapColor)
        drawRoundRect(
            color = faceColor,
            topLeft = Offset(0f, frontTop),
            size = Size(frontW, frontH),
            cornerRadius = radius
        )
        if (lit) {
            drawRoundRect(
                brush = litSheen,
                topLeft = Offset(0f, frontTop),
                size = Size(frontW, frontH * 0.5f),
                cornerRadius = radius
            )
            drawCircle(color = glint, radius = glintR, center = Offset(frontW * 0.28f, frontTop + frontH * 0.32f))
        } else {
            drawRoundRect(
                brush = unlitShade,
                topLeft = Offset(0f, frontTop),
                size = Size(frontW, frontH * 0.45f),
                cornerRadius = radius
            )
        }
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
