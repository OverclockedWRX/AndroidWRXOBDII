package com.wrxhud.hud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.MotionDisplayMode
import com.wrxhud.settings.ShiftLightPattern
import com.wrxhud.settings.TachVisualStyle
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val THRESHOLDS = listOf(4500, 4800, 5100, 5400, 5700, 6000, 6200, 6400, 6550, 6650)
private val THRESHOLDS11 = THRESHOLDS + 6700

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h360dp-xhdpi")
class ShiftLightsScreenshotTest(
    private val theme: HudColorTheme,
    private val style: TachVisualStyle,
    private val pattern: ShiftLightPattern,
    private val rpm: Float
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}-{3}")
        fun params(): List<Array<Any>> = buildList {
            for (t in HudColorTheme.entries) for (s in TachVisualStyle.entries) for (r in listOf(3000f, 5600f, 6900f)) {
                add(arrayOf(t, s, ShiftLightPattern.LEFT_TO_RIGHT, r))
            }
            for (s in TachVisualStyle.entries) for (p in ShiftLightPattern.entries.drop(1)) {
                add(arrayOf(HudColorTheme.CLASSIC, s, p, 5600f))
            }
        }
    }

    @Test
    fun lights() {
        ShotHarness.render(
            rule,
            name = "shift_${theme.name}_${style.name}_${pattern.name}_${rpm.toInt()}",
            theme = theme,
            width = 600.dp,
            height = 40.dp,
            advanceMs = 1000,
            content = {
                ShiftLights(
                    rpm = rpm,
                    thresholds = if (pattern.lampCount() == 11) THRESHOLDS11 else THRESHOLDS,
                    redline = 6700,
                    pattern = pattern,
                    visualStyle = style,
                    modifier = Modifier
                )
            }
        )
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h360dp-xhdpi")
class GearScreenshotTest(
    private val theme: HudColorTheme,
    private val gear: Int?,
    private val compact: Boolean
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun params(): List<Array<Any?>> = buildList {
            for (t in HudColorTheme.entries) for (g in listOf(null, -1, 3, 6)) for (c in listOf(false, true)) {
                add(arrayOf(t, g, c))
            }
        }
    }

    @Test
    fun gear() {
        ShotHarness.render(
            rule,
            name = "gear_${theme.name}_${gear ?: "N"}_${if (compact) "compact" else "full"}",
            theme = theme,
            width = 200.dp,
            height = 230.dp,
            advanceMs = 600,
            content = { GearIndicator(gear = gear, compact = compact) }
        )
    }
}

/**
 * Mid-flip / flash frames of the gear change animation. All goldens come from the pre-refactor code.
 *
 * Known one-frame difference: since flash/flip are read in the draw phase, the frame on which the
 * digit swaps shows the flash immediately; the old composition-phase read showed it one frame
 * (16 ms) later. So the capture at 140 ms is compared with the OLD 160 ms golden. The old
 * gearflip_*_140 goldens are kept, and the test asserts that this is still the only difference.
 * Every other 16 ms frame from 16 to 480 ms was compared old vs new when this was introduced and
 * was identical.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h360dp-xhdpi")
class GearFlipScreenshotTest(
    private val theme: HudColorTheme,
    private val captureMs: Long,
    private val goldenMs: Long
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-golden{2}")
        fun params(): List<Array<Any>> = buildList {
            for (t in HudColorTheme.entries) {
                for ((capture, golden) in listOf(64L to 64L, 140L to 160L, 160L to 160L, 200L to 200L)) {
                    add(arrayOf(t, capture, golden))
                }
            }
        }
    }

    @Test
    fun flip() {
        var gear by mutableStateOf<Int?>(2)
        if (captureMs != goldenMs && ShotHarness.mode != "record") {
            ShotHarness.assertGoldensDiffer("gearflip_${theme.name}_$captureMs", "gearflip_${theme.name}_$goldenMs")
        }
        // The shifted case never records: it would overwrite the golden it borrows.
        Assume.assumeTrue(captureMs == goldenMs || ShotHarness.mode != "record")
        ShotHarness.render(
            rule,
            name = "gearflip_${theme.name}_$goldenMs",
            theme = theme,
            width = 200.dp,
            height = 230.dp,
            advanceMs = 300,
            content = { GearIndicator(gear = gear, compact = false) },
            beforeCapture = { r ->
                ShotHarness.change(r, captureMs) { gear = 3 }
            }
        )
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h360dp-xhdpi")
class AttitudeScreenshotTest(
    private val theme: HudColorTheme,
    private val mode: MotionDisplayMode,
    private val case: Int
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        private val readings = listOf(
            MotionReading(4f, -7f, 0f, 0.45f, 0.3f, 0.5f, true),
            MotionReading(-18f, 22f, 0f, 1.1f, -0.9f, -0.6f, true)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun params(): List<Array<Any>> = buildList {
            for (t in HudColorTheme.entries) for (m in MotionDisplayMode.entries) for (c in readings.indices) {
                add(arrayOf(t, m, c))
            }
        }
    }

    @Test
    fun attitude() {
        ShotHarness.render(
            rule,
            name = "attitude_${theme.name}_${mode.name}_$case",
            theme = theme,
            width = 420.dp,
            height = 140.dp,
            advanceMs = 300,
            content = {
                MotionAttitudeDisplay(
                    motion = readings[case],
                    mode = mode,
                    visibility = MotionGaugeVisibility()
                )
            }
        )
    }
}
