package com.wrxhud.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wrxhud.settings.ShiftLightPattern
import com.wrxhud.settings.TachVisualStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Default sequential bar (L→R / R→L). */
const val SHIFT_LIGHT_COUNT_LINEAR = 10

/**
 * Outside→in / inside→out use one extra lamp so the bar is odd-length and
 * mirrored pairs land evenly around a true center.
 */
const val SHIFT_LIGHT_COUNT_SYMMETRIC = 11

fun ShiftLightPattern.lampCount(): Int = when (this) {
    ShiftLightPattern.OUTSIDE_IN, ShiftLightPattern.INSIDE_OUT -> SHIFT_LIGHT_COUNT_SYMMETRIC
    else -> SHIFT_LIGHT_COUNT_LINEAR
}

@Composable
fun ShiftLights(
    rpm: Float?,
    thresholds: List<Int>,
    redline: Int,
    pattern: ShiftLightPattern = ShiftLightPattern.LEFT_TO_RIGHT,
    visualStyle: TachVisualStyle = TachVisualStyle.BAR_3D,
    modifier: Modifier = Modifier
) {
    val flashOn by produceState(initialValue = true, rpm, redline) {
        if (rpm != null && rpm >= redline) {
            while (isActive) {
                value = !value
                delay(80)
            }
        } else {
            value = true
        }
    }

    val lampStates = remember(rpm, thresholds, pattern, flashOn) {
        resolveShiftLamps(rpm, thresholds, pattern, flashOn)
    }
    val stageCount = remember(pattern, thresholds) {
        pattern.lightingGroups().size
    }

    val lampOff = HudColors.LampOff
    val border = HudColors.Border
    val panel = HudColors.Panel

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visualStyle) {
        if (visualStyle == TachVisualStyle.LIQUID) {
            while (true) {
                withFrameNanos { ns ->
                    phase = ((ns % 3_500_000_000L).toFloat() / 3_500_000_000f)
                }
            }
        }
    }

    val tall = visualStyle != TachVisualStyle.HELIX
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (tall) 34.dp else 28.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lampStates.forEach { lamp ->
            val face = shiftLightColor(lamp.stageIndex, lamp.lit, stageCount)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(if (tall) 28.dp else 22.dp)
            ) {
                when (visualStyle) {
                    TachVisualStyle.LIQUID -> drawLampLiquid(
                        lit = lamp.lit,
                        faceColor = face,
                        lampOff = lampOff,
                        phase = phase + lamp.stageIndex * 0.15f
                    )
                    TachVisualStyle.RACE_SEG -> drawLampRaceSegment(
                        lit = lamp.lit,
                        faceColor = face,
                        lampOff = lampOff
                    )
                    TachVisualStyle.BAR_3D, TachVisualStyle.HELIX -> drawLamp3dEnhanced(
                        lit = lamp.lit,
                        faceColor = face,
                        lampOff = lampOff,
                        border = border,
                        panel = panel
                    )
                }
            }
        }
    }
}

data class ShiftLampState(
    val lit: Boolean,
    /** Stage in the RPM sequence (0 = first to light). Drives blue→green→amber→red. */
    val stageIndex: Int
)

/**
 * Maps RPM thresholds onto physical lamp slots for the chosen fill pattern.
 *
 * Linear patterns light one lamp per threshold.
 * Symmetric patterns light mirrored pairs together (plus a center lamp), and
 * use one extra physical lamp so the pairs stay even around the middle.
 */
fun resolveShiftLamps(
    rpm: Float?,
    thresholds: List<Int>,
    pattern: ShiftLightPattern,
    flashOn: Boolean = true
): List<ShiftLampState> {
    val lampCount = pattern.lampCount()
    val groups = pattern.lightingGroups()
    val stageRpms = sampleThresholds(thresholds, groups.size)
    val stageByPhysical = IntArray(lampCount) { -1 }
    groups.forEachIndexed { stage, lamps ->
        lamps.forEach { physical ->
            if (physical in 0 until lampCount) stageByPhysical[physical] = stage
        }
    }
    return List(lampCount) { physical ->
        val stage = stageByPhysical[physical]
        if (stage < 0 || stage >= stageRpms.size) {
            ShiftLampState(lit = false, stageIndex = 0)
        } else {
            val lit = rpm != null && rpm >= stageRpms[stage] && flashOn
            ShiftLampState(lit = lit, stageIndex = stage)
        }
    }
}

/**
 * Lamps that illuminate together at each stage, in fill order.
 * Symmetric patterns always light a mirrored pair (or the single center).
 */
fun ShiftLightPattern.lightingGroups(): List<List<Int>> {
    val count = lampCount()
    return when (this) {
        ShiftLightPattern.LEFT_TO_RIGHT ->
            (0 until count).map { listOf(it) }
        ShiftLightPattern.RIGHT_TO_LEFT ->
            (count - 1 downTo 0).map { listOf(it) }
        ShiftLightPattern.OUTSIDE_IN -> buildList {
            var left = 0
            var right = count - 1
            while (left < right) {
                add(listOf(left, right))
                left++
                right--
            }
            if (left == right) add(listOf(left))
        }
        ShiftLightPattern.INSIDE_OUT -> buildList {
            val mid = count / 2
            add(listOf(mid))
            var left = mid - 1
            var right = mid + 1
            while (left >= 0 && right < count) {
                add(listOf(left, right))
                left--
                right++
            }
        }
    }
}

/** @deprecated Prefer [lightingGroups]; kept for tests that assert linear order. */
fun ShiftLightPattern.physicalOrder(count: Int): List<Int> =
    lightingGroups().flatten().take(count.coerceAtLeast(0))

fun sampleThresholds(thresholds: List<Int>, stageCount: Int): List<Int> {
    if (stageCount <= 0) return emptyList()
    if (thresholds.isEmpty()) return emptyList()
    if (stageCount == 1) return listOf(thresholds.first())
    if (thresholds.size == stageCount) return thresholds
    return List(stageCount) { i ->
        val idx = (i * (thresholds.size - 1).toFloat() / (stageCount - 1))
            .toInt()
            .coerceIn(0, thresholds.lastIndex)
        thresholds[idx]
    }
}

fun shiftLightColor(stageIndex: Int, lit: Boolean, stageCount: Int = SHIFT_LIGHT_COUNT_LINEAR): Color {
    if (!lit) return HudColors.LampOff
    val last = (stageCount - 1).coerceAtLeast(1)
    val t = stageIndex.toFloat() / last
    // Blue → green → amber → red as RPM climbs toward redline.
    return when {
        t >= 0.8f -> HudColors.Red
        t >= 0.55f -> HudColors.Amber
        t >= 0.3f -> HudColors.Green
        else -> HudColors.BoostBlue
    }
}
