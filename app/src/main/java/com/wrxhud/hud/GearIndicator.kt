package com.wrxhud.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.settings.MotionSetting
import com.wrxhud.vehicle.UnitConverters
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Boxed gear readout with a mechanical flip-clock transition on change.
 */
@Composable
fun GearIndicator(
    gear: Int?,
    modifier: Modifier = Modifier,
    /** Slightly smaller box when SPD/LIM sit beside it. */
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(10.dp)
    val label = UnitConverters.formatGear(gear)
    var displayed by remember { mutableStateOf(label) }
    val flip = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var previousGear by remember { mutableStateOf(gear) }
    val density = LocalDensity.current
    val perspectivePx = with(density) { 16.dp.toPx() }

    val motion = currentHudMotion()
    LaunchedEffect(gear, motion) {
        val next = UnitConverters.formatGear(gear)
        if (next == displayed) {
            previousGear = gear
            return@LaunchedEffect
        }

        if (motion != MotionSetting.FULL) {
            flip.snapTo(0f)
            flash.snapTo(0f)
            displayed = next
            previousGear = gear
            return@LaunchedEffect
        }

        val upshift = gearSortKey(gear) >= gearSortKey(previousGear)
        val outAngle = if (upshift) -90f else 90f
        val inAngle = if (upshift) 90f else -90f

        flip.snapTo(0f)
        flip.animateTo(outAngle, tween(110, easing = FastOutLinearInEasing))
        displayed = next
        previousGear = gear
        flip.snapTo(inAngle)

        launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
        }
        flip.animateTo(0f, tween(160, easing = LinearOutSlowInEasing))
    }

    // Theme colors are read here (rare changes); the animated flip/flash values are only read
    // inside draw / graphicsLayer lambdas so a gear change doesn't recompose per frame.
    val baseBorder = HudColors.Border
    val basePanel = HudColors.Panel
    val accent = HudColors.Accent
    val flashTint = remember(accent) { accent.copy(alpha = 0.22f) }
    val flipProvider = remember(flip) { { flip.value } }
    val flashProvider = remember(flash) { { flash.value } }

    val boxW = if (compact) 72.dp else TachSideClusterDims.width
    val boxH = if (compact) 80.dp else TachSideClusterDims.columnHeight
    val gearSp = if (compact) 48.sp else TachSideClusterDims.gearSp

    if (compact) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GEAR",
                color = HudColors.TextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            GearFace(
                displayed = displayed,
                boxW = boxW,
                boxH = boxH,
                gearSp = gearSp,
                shape = shape,
                basePanel = basePanel,
                baseBorder = baseBorder,
                accent = accent,
                flashTint = flashTint,
                flip = flipProvider,
                flash = flashProvider,
                perspectivePx = perspectivePx,
                density = density
            )
        }
    } else {
        // Side cluster: no caption — face matches SPD/LIM stack height.
        GearFace(
            displayed = displayed,
            boxW = boxW,
            boxH = boxH,
            gearSp = gearSp,
            shape = shape,
            basePanel = basePanel,
            baseBorder = baseBorder,
            accent = accent,
            flashTint = flashTint,
            flip = flipProvider,
            flash = flashProvider,
            perspectivePx = perspectivePx,
            density = density,
            modifier = modifier
        )
    }
}

@Composable
private fun GearFace(
    displayed: String,
    boxW: Dp,
    boxH: Dp,
    gearSp: TextUnit,
    shape: RoundedCornerShape,
    basePanel: Color,
    baseBorder: Color,
    accent: Color,
    flashTint: Color,
    flip: () -> Float,
    flash: () -> Float,
    perspectivePx: Float,
    density: Density,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = boxW, height = boxH)
            .clip(shape)
            .drawWithCache {
                // Outline and stroke are cached; only the colors follow the flash animation.
                val outline = shape.createOutline(size, layoutDirection, this)
                // Same geometry as Modifier.border(2.dp, color, RoundedCornerShape(10.dp)).
                val strokeW = 2.dp.toPx()
                val half = strokeW / 2f
                val stroke = Stroke(strokeW)
                val strokeCorner = CornerRadius(10.dp.toPx() - half)
                onDrawWithContent {
                    val f = flash()
                    drawOutline(outline, lerp(basePanel, flashTint, f * 0.5f))
                    drawContent()
                    drawRoundRect(
                        color = lerp(baseBorder, accent, f),
                        topLeft = Offset(half, half),
                        size = Size(size.width - strokeW, size.height - strokeW),
                        cornerRadius = strokeCorner,
                        style = stroke
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayed,
            color = HudColors.TextPrimary,
            fontSize = gearSp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.graphicsLayer {
                val flipAngle = flip()
                rotationX = flipAngle
                cameraDistance = perspectivePx * density.density
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                alpha = 1f - (abs(flipAngle) / 90f) * 0.55f
                val scalePunch = 1f + (0.08f * flash())
                scaleX = scalePunch
                scaleY = scalePunch
            }
        )
    }
}

private fun gearSortKey(gear: Int?): Int = when (gear) {
    null -> 0
    -1 -> -1
    else -> gear
}
