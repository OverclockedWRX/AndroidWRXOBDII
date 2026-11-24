package com.wrxhud.hud

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.MotionDisplayMode
import com.wrxhud.settings.TachVisualStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tachometer (bar/liquid/helix/race), side cluster, gear and G gauge for every theme x style. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h360dp-xhdpi")
class TachScreenshotTest(
    private val theme: HudColorTheme,
    private val style: TachVisualStyle,
    private val case: Int
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        // rpm, boost psi, throttle %, motion mode
        private val cases = listOf(
            listOf(900f, 0f, 5f, MotionDisplayMode.RINGS),
            listOf(4200f, 6f, 55f, MotionDisplayMode.RINGS),
            listOf(6300f, 14f, 75f, MotionDisplayMode.TEXT),
            listOf(6900f, 20f, 97f, MotionDisplayMode.RINGS)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun params(): List<Array<Any>> = buildList {
            for (t in HudColorTheme.entries) for (s in TachVisualStyle.entries) for (c in cases.indices) {
                add(arrayOf(t, s, c))
            }
        }
    }

    @Test
    fun tach() {
        val (rpm, boost, throttle, mode) = cases[case]
        val reading = mutableStateOf(MotionReading(0.2f, 0.4f, 0f, 0.45f, 0.2f, 0.4f, true))
        ShotHarness.render(
            rule,
            name = "tach_${theme.name}_${style.name}_$case",
            theme = theme,
            width = 900.dp,
            height = 360.dp,
            advanceMs = 1000,
            content = {
                Tachometer(
                    rpm = rpm as Float,
                    previousRpm = rpm,
                    sampleAgeMs = 50,
                    redline = 6700,
                    gear = if (case == 3) -1 else 3,
                    motion = { reading.value },
                    motionDisplayMode = mode as MotionDisplayMode,
                    visualStyle = style,
                    boostPsi = boost as Float,
                    throttlePercent = throttle as Float,
                    showThrottle = true,
                    speedValue = "72",
                    speedUnit = "MPH"
                )
            },
            beforeCapture = { r ->
                // Build a short G trail: three further samples, a frame batch apart.
                val seq = listOf(
                    MotionReading(0f, 0f, 0f, 0.6f, 0.3f, 0.7f, true),
                    MotionReading(0f, 0f, 0f, 0.9f, 0.6f, 0.9f, true),
                    MotionReading(0f, 0f, 0f, 0.5f, -0.2f, 0.3f, true)
                )
                for (m in seq) {
                    reading.value = m
                    r.mainClock.advanceTimeBy(100)
                }
            }
        )
    }
}
