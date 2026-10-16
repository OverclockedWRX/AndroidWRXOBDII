package com.wrxhud.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wrxhud.vehicle.UnitConverters
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Boxed gear readout with a mechanical flip-clock transition on change.
 */
@Composable
fun GearIndicator(
    gear: Int?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val label = UnitConverters.formatGear(gear)
    var displayed by remember { mutableStateOf(label) }
    val flip = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var previousGear by remember { mutableStateOf(gear) }
    val density = LocalDensity.current
    val perspectivePx = with(density) { 16.dp.toPx() }

    LaunchedEffect(gear) {
        val next = UnitConverters.formatGear(gear)
        if (next == displayed) {
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

    val borderColor = lerp(HudColors.Border, HudColors.Accent, flash.value)
    val panelColor = lerp(HudColors.Panel, HudColors.Accent.copy(alpha = 0.22f), flash.value * 0.5f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GEAR",
            color = HudColors.TextDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .size(width = 118.dp, height = 128.dp)
                .clip(shape)
                .background(panelColor, shape)
                .border(2.dp, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayed,
                color = HudColors.TextPrimary,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.graphicsLayer {
                    rotationX = flip.value
                    cameraDistance = perspectivePx * density.density
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = 1f - (abs(flip.value) / 90f) * 0.55f
                    val scalePunch = 1f + (0.08f * flash.value)
                    scaleX = scalePunch
                    scaleY = scalePunch
                }
            )
        }
    }
}

private fun gearSortKey(gear: Int?): Int = when (gear) {
    null -> 0
    -1 -> -1
    else -> gear
}
