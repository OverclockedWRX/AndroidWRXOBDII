package com.wrxhud.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wrxhud.settings.ShiftLightPattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val LAMP_COUNT = 10

@Composable
fun ShiftLights(
    rpm: Float?,
    thresholds: List<Int>,
    redline: Int,
    pattern: ShiftLightPattern = ShiftLightPattern.LEFT_TO_RIGHT,
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lampStates.forEach { lamp ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shiftLightColor(lamp.stageIndex, lamp.lit))
            )
        }
    }
}

data class ShiftLampState(
    val lit: Boolean,
    /** Stage in the RPM sequence (0 = first to light). Drives green→amber→red. */
    val stageIndex: Int
)

/**
 * Maps RPM thresholds onto physical lamp slots for the chosen fill pattern.
 * Thresholds are always sequential (1st crossed → earliest stage, …);
 * [pattern] only changes which physical lamp lights at each stage.
 */
fun resolveShiftLamps(
    rpm: Float?,
    thresholds: List<Int>,
    pattern: ShiftLightPattern,
    flashOn: Boolean = true
): List<ShiftLampState> {
    val stages = thresholds.take(LAMP_COUNT)
    val order = pattern.physicalOrder(stages.size)
    val stageByPhysical = IntArray(LAMP_COUNT) { -1 }
    order.forEachIndexed { stage, physical ->
        if (physical in 0 until LAMP_COUNT) stageByPhysical[physical] = stage
    }
    return List(LAMP_COUNT) { physical ->
        val stage = stageByPhysical[physical]
        if (stage < 0 || stage >= stages.size) {
            ShiftLampState(lit = false, stageIndex = 0)
        } else {
            val lit = rpm != null && rpm >= stages[stage] && flashOn
            ShiftLampState(lit = lit, stageIndex = stage)
        }
    }
}

fun ShiftLightPattern.physicalOrder(count: Int): List<Int> {
    val n = count.coerceIn(0, LAMP_COUNT)
    val full = when (this) {
        ShiftLightPattern.LEFT_TO_RIGHT -> (0 until LAMP_COUNT).toList()
        ShiftLightPattern.RIGHT_TO_LEFT -> (LAMP_COUNT - 1 downTo 0).toList()
        ShiftLightPattern.OUTSIDE_IN -> buildList {
            var left = 0
            var right = LAMP_COUNT - 1
            while (left <= right) {
                add(left)
                if (left != right) add(right)
                left++
                right--
            }
        }
        ShiftLightPattern.INSIDE_OUT -> buildList {
            var left = (LAMP_COUNT - 1) / 2
            var right = LAMP_COUNT / 2
            val used = BooleanArray(LAMP_COUNT)
            while (left >= 0 || right < LAMP_COUNT) {
                if (left >= 0 && !used[left]) {
                    add(left)
                    used[left] = true
                }
                if (right < LAMP_COUNT && !used[right]) {
                    add(right)
                    used[right] = true
                }
                left--
                right++
            }
        }
    }
    return full.take(n)
}

fun shiftLightColor(stageIndex: Int, lit: Boolean): Color {
    if (!lit) return HudColors.LampOff
    return when {
        stageIndex >= 8 -> HudColors.Red
        stageIndex >= 5 -> HudColors.Amber
        else -> HudColors.Green
    }
}
