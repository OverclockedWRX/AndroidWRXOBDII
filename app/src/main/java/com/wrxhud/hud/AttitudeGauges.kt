package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.Density
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
    modifier: Modifier = Modifier,
    /** Smaller rings for the tach row (aligned with RPM bar height). */
    compact: Boolean = false
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
            modifier = modifier,
            ringSize = if (compact) 52.dp else 76.dp
        )
        MotionDisplayMode.COMBINED -> CombinedAttitudeRing(
            motion = motion,
            visibility = visibility,
            modifier = modifier,
            ringSize = if (compact) 56.dp else 84.dp
        )
    }
}

/**
 * G-force tile for the tach side cluster — reading inside the border, above the circle.
 */
@Composable
fun TachMotionCluster(
    /** Provider so the ~60 Hz sensor readings are read in small scopes / draw, not here. */
    motion: () -> MotionReading,
    mode: MotionDisplayMode,
    visibility: MotionGaugeVisibility,
    modifier: Modifier = Modifier
) {
    if (!visibility.gForce) return
    val shape = RoundedCornerShape(8.dp)
    val palette = HudColors.Attitude

    Box(
        modifier = modifier
            .size(
                width = TachSideClusterDims.width,
                height = TachSideClusterDims.columnHeight
            )
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(2.dp, HudColors.Border, shape)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        if (mode == MotionDisplayMode.TEXT) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GForceCell(motion)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GForceText(
                    motion = motion,
                    color = palette.glow,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val side = minOf(maxWidth, maxHeight)
                    GForceGauge(motion = motion, size = side, showValue = false)
                }
            }
        }
    }
}

/** G readout: recomposes only when the formatted text changes. */
@Composable
private fun GForceText(
    motion: () -> MotionReading,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val text by remember(motion) { derivedStateOf { formatGForce(motion().gForce) } }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun GForceCell(motion: () -> MotionReading) {
    val text by remember(motion) { derivedStateOf { formatGForce(motion().gForce) } }
    MotionCell("G", text)
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
    modifier: Modifier = Modifier,
    ringSize: Dp = 76.dp
) {
    val shape = RoundedCornerShape(8.dp)
    val gap = if (ringSize <= 56.dp) 6.dp else 10.dp
    val pad = if (ringSize <= 56.dp) 3.dp else 4.dp
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = if (ringSize <= 56.dp) 4.dp else 6.dp, vertical = pad),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom
    ) {
        if (visibility.pitch) {
            InclinometerGauge(
                degrees = motion.pitchDeg,
                kind = InclinometerKind.PITCH,
                size = ringSize
            )
        }
        if (visibility.roll) {
            InclinometerGauge(
                degrees = motion.rollDeg,
                kind = InclinometerKind.ROLL,
                size = ringSize
            )
        }
        if (visibility.gForce) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatGForce(motion.gForce),
                    color = HudColors.Attitude.glow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                GForceGauge(motion = { motion }, size = ringSize, showValue = false)
            }
        }
    }
}

@Composable
private fun CombinedAttitudeRing(
    motion: MotionReading,
    visibility: MotionGaugeVisibility,
    modifier: Modifier = Modifier,
    ringSize: Dp = 84.dp
) {
    val shape = RoundedCornerShape(8.dp)
    val side = if (ringSize <= 60.dp) ringSize else 76.dp
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudColors.Panel, shape)
            .border(1.dp, HudColors.Border, shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        when {
            visibility.pitch && visibility.roll -> {
                InclinometerGauge(
                    degrees = motion.pitchDeg,
                    secondaryDegrees = motion.rollDeg,
                    kind = InclinometerKind.COMBINED,
                    size = ringSize
                )
            }
            visibility.pitch -> {
                InclinometerGauge(
                    degrees = motion.pitchDeg,
                    kind = InclinometerKind.PITCH,
                    size = side
                )
            }
            visibility.roll -> {
                InclinometerGauge(
                    degrees = motion.rollDeg,
                    kind = InclinometerKind.ROLL,
                    size = side
                )
            }
        }
        if (visibility.gForce) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatGForce(motion.gForce),
                    color = HudColors.Attitude.glow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                GForceGauge(motion = { motion }, size = side, showValue = false)
            }
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

/** Fixed-size ring buffer of recent G samples; [version] is read in draw to trigger redraws. */
private class GTrail {
    val xs = FloatArray(G_TRAIL_MAX)
    val ys = FloatArray(G_TRAIL_MAX)
    var count = 0
        private set
    private var start = 0
    var version by mutableIntStateOf(0)
        private set

    fun add(x: Float, y: Float) {
        if (count > 0) {
            val last = (start + count - 1) % G_TRAIL_MAX
            if (hypot((xs[last] - x).toDouble(), (ys[last] - y).toDouble()) <= 0.008) return
        }
        if (count < G_TRAIL_MAX) {
            val i = (start + count) % G_TRAIL_MAX
            xs[i] = x
            ys[i] = y
            count++
        } else {
            xs[start] = x
            ys[start] = y
            start = (start + 1) % G_TRAIL_MAX
        }
        version++
    }

    fun xAt(i: Int): Float = xs[(start + i) % G_TRAIL_MAX]
    fun yAt(i: Int): Float = ys[(start + i) % G_TRAIL_MAX]
}

@Composable
private fun GForceGauge(
    motion: () -> MotionReading,
    size: Dp,
    /** When false, only the circle is drawn (value is shown by the caller above). */
    showValue: Boolean = true
) {
    val trail = remember { GTrail() }
    val provider = rememberUpdatedState(motion)
    LaunchedEffect(trail) {
        snapshotFlow {
            val m = provider.value()
            val lat = m.lateralG
            val lon = m.longitudinalG
            if (lat != null && lon != null) Offset(lat, lon) else Offset.Unspecified
        }.collect { sample ->
            if (sample.isSpecified) trail.add(sample.x, sample.y)
        }
    }
    val palette = HudColors.Attitude

    val labelSp = when {
        size >= 100.dp -> 16.sp
        size >= 72.dp -> 13.sp
        else -> 10.sp
    }
    val drawModifier = remember(palette) {
        Modifier.drawWithCache {
            val face = GForceFaceRenderer(this, this.size, palette)
            onDrawBehind {
                val m = provider.value()
                val lat = m.lateralG
                val lon = m.longitudinalG
                face.draw(
                    this,
                    lateralG = lat ?: 0f,
                    longitudinalG = lon ?: 0f,
                    hasSample = lat != null && lon != null,
                    trail = trail,
                    trailVersion = trail.version
                )
            }
        }
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxSize().then(drawModifier))
        if (showValue) {
            GForceText(
                motion = provider.value,
                color = palette.glow,
                fontSize = labelSp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }
    }
}

/** Static ring of 52 segments (upper/lower colors), geometry precomputed per size. */
private class SegmentedRing(
    density: Density,
    cx: Float,
    cy: Float,
    r: Float,
    private val palette: AttitudePalette
) {
    private val segments = 52
    private val sweep = 360f / segments
    private val gap = sweep * 0.28f
    private val stroke = Stroke(width = r * 0.11f, cap = StrokeCap.Butt)
    private val inset = r * 0.04f
    private val topLeft = Offset(cx - r + inset, cy - r + inset)
    private val arcSize = Size((r - inset) * 2f, (r - inset) * 2f)
    private val starts = FloatArray(segments) { i -> -90f + i * sweep + gap / 2f }
    private val upper = BooleanArray(segments) { i ->
        val mid = starts[i] + (sweep - gap) / 2f
        // Android angles: 0° = 3 o'clock, clockwise; sin>0 → below center.
        sin(Math.toRadians(mid.toDouble())) < 0.0
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (i in 0 until segments) {
            drawArc(
                color = if (upper[i]) palette.ringUpper else palette.ringLower,
                startAngle = starts[i],
                sweepAngle = sweep - gap,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
    }
}

private class GForceFaceRenderer(density: Density, size: Size, private val palette: AttitudePalette) {
    private val cx = size.width / 2f
    private val cy = size.height / 2f
    private val r = size.minDimension / 2f - with(density) { 3.dp.toPx() }
    private val plotR = r * 0.72f
    private val ring = SegmentedRing(density, cx, cy, r, palette)
    private val scaleStroke = Stroke(width = with(density) { 1.dp.toPx() })
    private val scaleColor = palette.horizon.copy(alpha = 0.22f)
    private val crossColor = palette.horizon.copy(alpha = 0.45f)
    private val crossW = with(density) { 1.2.dp.toPx() }
    private val trailStroke = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round)
    private val trailColor = palette.ringLower.copy(alpha = 0.55f)
    private val dotR = with(density) { 1.6.dp.toPx() }
    private val tipHaloR = with(density) { 7.dp.toPx() }
    private val tipR = with(density) { 3.5.dp.toPx() }
    private val tipInkR = with(density) { 1.4.dp.toPx() }
    private val haloColor = palette.glow.copy(alpha = 0.35f)
    private val path = Path()

    private fun px(sample: Float): Float {
        val x = (sample / G_FULL_SCALE).coerceIn(-1.15f, 1.15f)
        return cx + x * plotR
    }

    private fun py(sample: Float): Float {
        val y = (-sample / G_FULL_SCALE).coerceIn(-1.15f, 1.15f) // +longitudinal → up
        return cy + y * plotR
    }

    @Suppress("UNUSED_PARAMETER")
    fun draw(
        scope: DrawScope,
        lateralG: Float,
        longitudinalG: Float,
        hasSample: Boolean,
        trail: GTrail,
        trailVersion: Int
    ) = with(scope) {
        drawCircle(color = palette.face, radius = r * 0.92f, center = Offset(cx, cy))
        ring.draw(this)

        // Scale rings at 0.5g / 1.0g / full scale
        drawCircle(color = scaleColor, radius = plotR * (0.5f / G_FULL_SCALE), center = Offset(cx, cy), style = scaleStroke)
        drawCircle(color = scaleColor, radius = plotR * (1f / G_FULL_SCALE), center = Offset(cx, cy), style = scaleStroke)
        drawCircle(color = scaleColor, radius = plotR * 1f, center = Offset(cx, cy), style = scaleStroke)
        // Crosshairs
        drawLine(crossColor, Offset(cx - plotR, cy), Offset(cx + plotR, cy), crossW)
        drawLine(crossColor, Offset(cx, cy - plotR), Offset(cx, cy + plotR), crossW)

        // History trail (older = dimmer)
        val n = trail.count
        if (n >= 2) {
            path.rewind()
            for (i in 0 until n) {
                val x = px(trail.xAt(i))
                val y = py(trail.yAt(i))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = trailColor, style = trailStroke)
            for (i in 0 until n) {
                val alpha = 0.15f + 0.55f * (i + 1).toFloat() / n
                drawCircle(
                    color = palette.glow.copy(alpha = alpha),
                    radius = dotR,
                    center = Offset(px(trail.xAt(i)), py(trail.yAt(i)))
                )
            }
        }

        if (hasSample) {
            val tip = Offset(px(lateralG), py(longitudinalG))
            drawCircle(color = haloColor, radius = tipHaloR, center = tip)
            drawCircle(color = palette.glow, radius = tipR, center = tip)
            drawCircle(color = palette.ink, radius = tipInkR, center = tip)
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

    val palette = HudColors.Attitude
    // Angles are read in the draw lambda, so new sensor values redraw without rebuilding the cache.
    val degState = rememberUpdatedState(deg)
    val rollState = rememberUpdatedState(roll)
    val drawModifier = remember(palette, kind) {
        Modifier.drawWithCache {
            val face = InclinometerFaceRenderer(this, this.size, kind, palette)
            onDrawBehind {
                face.draw(
                    this,
                    pitchDeg = degState.value,
                    rollDeg = if (kind == InclinometerKind.COMBINED) rollState.value else degState.value
                )
            }
        }
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxSize().then(drawModifier))
        Text(
            text = valueLabel,
            color = palette.glow,
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

private class InclinometerFaceRenderer(
    density: Density,
    size: Size,
    private val kind: InclinometerKind,
    private val palette: AttitudePalette
) {
    private val cx = size.width / 2f
    private val cy = size.height / 2f
    private val r = size.minDimension / 2f - with(density) { 3.dp.toPx() }
    private val ring = SegmentedRing(density, cx, cy, r, palette)
    private val horizonColor = palette.horizon.copy(alpha = 0.55f)
    private val horizonW = with(density) { 1.2.dp.toPx() }
    private val carOrigin = Offset(cx, cy + r * 0.08f)
    private val car = CarSilhouette(
        side = kind == InclinometerKind.PITCH,
        s = when (kind) {
            InclinometerKind.PITCH, InclinometerKind.ROLL -> r * 0.058f
            InclinometerKind.COMBINED -> r * 0.042f
        },
        palette = palette,
        outlineWidth = with(density) { 1.4.dp.toPx() }
    )

    fun draw(scope: DrawScope, pitchDeg: Float, rollDeg: Float) = with(scope) {
        drawCircle(color = palette.face, radius = r * 0.92f, center = Offset(cx, cy))
        ring.draw(this)

        // Horizon reference through 0°
        drawLine(horizonColor, Offset(cx - r * 0.72f, cy), Offset(cx + r * 0.72f, cy), horizonW)

        // Bias car slightly below center so the degree readout above stays clear.
        // Pivot at Offset.Zero so tilt/roll spins the silhouette in place (default
        // rotate() pivot is the canvas center and swung the car out of the ring).
        translate(carOrigin.x, carOrigin.y) {
            when (kind) {
                InclinometerKind.PITCH -> {
                    // Screen-remapped pitch: positive = nose up → clockwise for side-view nose-right.
                    val tilt = pitchDeg.coerceIn(-45f, 45f)
                    rotate(degrees = tilt, pivot = Offset.Zero) { car.draw(this) }
                }
                InclinometerKind.ROLL -> {
                    // Screen-remapped roll: positive = lean right → clockwise for rear view.
                    val tilt = rollDeg.coerceIn(-45f, 45f)
                    rotate(degrees = tilt, pivot = Offset.Zero) { car.draw(this) }
                }
                InclinometerKind.COMBINED -> {
                    val p = pitchDeg.coerceIn(-35f, 35f)
                    val ro = rollDeg.coerceIn(-35f, 35f)
                    rotate(degrees = ro, pivot = Offset.Zero) {
                        val foreshorten = (1f - p / 100f).coerceIn(0.65f, 1.15f)
                        scale(scaleX = 1f, scaleY = foreshorten, pivot = Offset.Zero) { car.draw(this) }
                    }
                }
            }
        }
    }
}

/**
 * 2022+ WRX VB silhouette, side view (sedan rake, hood scoop, trunk wing, square-ish nose; nose
 * points +X) or rear view (wide fenders, trunk wing, hexagonal cues). Paths and the body
 * gradient are built once per size/palette; fill/glow follow the active theme.
 */
private class CarSilhouette(
    private val side: Boolean,
    private val s: Float,
    private val palette: AttitudePalette,
    private val outlineWidth: Float
) {
    private val glow = palette.glow
    private val ink = palette.ink
    private val body = Path()
    private val window = Path()
    private val bodyBrush: Brush
    private val windowColor = ink.copy(alpha = 0.55f)
    private val outline = Stroke(width = outlineWidth)
    private val outlineColor: Color
    private val rearWindowCorner = CornerRadius(0.6f * s, 0.6f * s)

    init {
        if (side) {
            body.apply {
                moveTo(-9.2f * s, 2.4f * s)
                lineTo(-8.6f * s, 1.2f * s)
                lineTo(-7.8f * s, -0.2f * s)
                lineTo(-6.2f * s, -1.0f * s)
                lineTo(-5.4f * s, -2.4f * s)
                lineTo(-4.6f * s, -1.2f * s)
                lineTo(-1.2f * s, -2.6f * s)
                lineTo(2.8f * s, -2.5f * s)
                lineTo(4.2f * s, -1.1f * s)
                lineTo(5.0f * s, -1.35f * s)
                lineTo(6.2f * s, -1.0f * s)
                lineTo(7.4f * s, -0.35f * s)
                lineTo(9.0f * s, 0.55f * s)
                lineTo(9.1f * s, 2.4f * s)
                close()
            }
            window.apply {
                moveTo(-3.8f * s, -0.4f * s)
                lineTo(-1.0f * s, -2.2f * s)
                lineTo(2.4f * s, -2.1f * s)
                lineTo(3.6f * s, -0.5f * s)
                close()
            }
            bodyBrush = Brush.verticalGradient(
                colors = listOf(glow, glow.copy(alpha = 0.75f)),
                startY = -3f * s,
                endY = 3f * s
            )
            outlineColor = glow.copy(alpha = 0.35f)
        } else {
            body.apply {
                moveTo(-7.2f * s, 2.6f * s)
                lineTo(-6.4f * s, 0.2f * s)
                lineTo(-5.2f * s, -1.6f * s)
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
            bodyBrush = Brush.verticalGradient(
                colors = listOf(glow, glow.copy(alpha = 0.7f)),
                startY = -3.5f * s,
                endY = 3.5f * s
            )
            outlineColor = glow.copy(alpha = 0.3f)
        }
    }

    private val wheelInk = ink.copy(alpha = 0.9f)
    private val wheelGlow = glow.copy(alpha = 0.4f)
    private val rearInkSmall = ink.copy(alpha = 0.85f)

    fun draw(scope: DrawScope) = with(scope) {
        if (side) {
            drawPath(body, brush = bodyBrush)
            drawPath(window, color = windowColor)
            drawPath(body, color = outlineColor, style = outline)
            drawCircle(wheelInk, 1.85f * s, Offset(-5.2f * s, 2.85f * s))
            drawCircle(wheelInk, 1.85f * s, Offset(5.0f * s, 2.85f * s))
            drawCircle(wheelGlow, 0.7f * s, Offset(-5.2f * s, 2.85f * s))
            drawCircle(wheelGlow, 0.7f * s, Offset(5.0f * s, 2.85f * s))
        } else {
            drawPath(body, brush = bodyBrush)
            drawRoundRect(
                color = windowColor,
                topLeft = Offset(-3.4f * s, -1.8f * s),
                size = Size(6.8f * s, 2.6f * s),
                cornerRadius = rearWindowCorner
            )
            drawLine(
                color = glow,
                start = Offset(-3.4f * s, -3.25f * s),
                end = Offset(3.4f * s, -3.25f * s),
                strokeWidth = 2.2f * s.coerceAtLeast(1f)
            )
            drawPath(body, color = outlineColor, style = outline)
            drawCircle(rearInkSmall, 0.7f * s, Offset(-2.8f * s, 3.35f * s))
            drawCircle(rearInkSmall, 0.7f * s, Offset(2.8f * s, 3.35f * s))
            drawCircle(wheelInk, 1.7f * s, Offset(-5.0f * s, 3.4f * s))
            drawCircle(wheelInk, 1.7f * s, Offset(5.0f * s, 3.4f * s))
        }
    }
}
