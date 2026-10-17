package com.wrxhud.ui

import android.app.Activity
import android.util.Log
import android.view.Display
import android.view.View

/**
 * Asks the system to run this window at the panel's highest refresh rate
 * (60 / 90 / 120 / 144+ Hz as supported), and not power-throttle ARR down.
 *
 * Display mode + [View.setRequestedFrameRate] are the platform hooks; Compose still
 * only draws when state changes or a [androidx.compose.runtime.withFrameNanos] loop runs.
 */
object HighRefreshRate {
    private const val TAG = "HighRefreshRate"
    private const val MIN_TARGET_HZ = 60f

    fun apply(activity: Activity) {
        val display = activity.display ?: return
        val best = pickBestMode(display) ?: return

        val attrs = activity.window.attributes
        attrs.preferredRefreshRate = best.refreshRate
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs

        // Adaptive refresh can drop a mostly-static HUD to 60 Hz; keep the race panel high.
        activity.window.setFrameRatePowerSavingsBalanced(false)

        val root = activity.window.decorView
        root.post { requestOnView(root, best.refreshRate) }

        val supported = display.supportedModes
            .map { it.refreshRate }
            .distinct()
            .sorted()
        Log.i(
            TAG,
            "Prefer %.1f Hz (mode %d); device modes=%s".format(
                best.refreshRate,
                best.modeId,
                supported.joinToString { "%.0f".format(it) }
            )
        )
    }

    /**
     * Hint the Compose / content view to match [hz] (or the display max / HIGH category).
     * Safe to call repeatedly from onResume / DisposableEffect.
     */
    fun requestOnView(view: View, hz: Float = 0f) {
        val rate = when {
            hz >= MIN_TARGET_HZ -> hz
            else -> view.display?.supportedModes
                ?.maxOfOrNull { it.refreshRate }
                ?.takeIf { it >= MIN_TARGET_HZ }
                ?: View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        view.setRequestedFrameRate(rate)
    }

    private fun pickBestMode(display: Display): Display.Mode? {
        val modes = display.supportedModes
        if (modes.isEmpty()) return null
        val current = display.mode
        // Highest refresh first; prefer current resolution on ties (avoid resolution switches).
        return modes.maxWithOrNull(
            compareBy<Display.Mode> { it.refreshRate }
                .thenByDescending {
                    if (it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                    ) {
                        1
                    } else {
                        0
                    }
                }
        )
    }
}
