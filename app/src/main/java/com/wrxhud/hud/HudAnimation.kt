package com.wrxhud.hud

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wrxhud.settings.MotionSetting

/** False when the user turned system animations off (animator duration scale = 0). */
internal fun animatorsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** The user's motion preference; null = follow the system. Provided by [HudTheme]. */
internal val LocalHudMotion = compositionLocalOf<MotionSetting?> { null }

internal fun resolveMotion(preference: MotionSetting?, context: Context): MotionSetting =
    preference ?: if (animatorsEnabled(context)) MotionSetting.FULL else MotionSetting.REDUCED

/** Effective motion level for the current composition. */
@Composable
internal fun currentHudMotion(): MotionSetting =
    resolveMotion(LocalHudMotion.current, LocalContext.current)

/**
 * Looping 0..1 phase for decorative animations (liquid wave, helix twist, liquid lamps).
 *
 * The loop only runs while [active] AND the host lifecycle is RESUMED, and it is not started at
 * all unless motion is [MotionSetting.FULL] (the phase then stays frozen). The returned state is
 * meant to be read inside draw lambdas so the parent never recomposes per frame.
 * The phase is `frameTime % periodNs / periodNs`, so it is a pure function of the frame clock.
 */
@Composable
internal fun rememberLoopPhase(active: Boolean, periodNs: Long): MutableFloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val motion = LocalHudMotion.current
    LaunchedEffect(active, periodNs, owner, motion) {
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (resolveMotion(motion, context) != MotionSetting.FULL) return@repeatOnLifecycle
            val period = periodNs.toFloat()
            while (true) {
                withFrameNanos { ns -> phase.floatValue = (ns % periodNs).toFloat() / period }
            }
        }
    }
    return phase
}
