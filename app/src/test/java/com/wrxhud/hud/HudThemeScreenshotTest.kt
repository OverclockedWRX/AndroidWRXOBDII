package com.wrxhud.hud

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.wrxhud.gps.SpeedLimitReading
import com.wrxhud.sensors.MotionReading
import com.wrxhud.settings.HudColorTheme
import com.wrxhud.settings.HudPreferences
import com.wrxhud.settings.TachVisualStyle
import com.wrxhud.vehicle.VehicleSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Whole HUD for every theme x tach style. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h400dp-xhdpi")
class HudThemeScreenshotTest(
    private val theme: HudColorTheme,
    private val style: TachVisualStyle
) {
    @get:Rule
    val rule = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = buildList {
            for (t in HudColorTheme.entries) for (s in TachVisualStyle.entries) add(arrayOf(t, s))
        }
    }

    @Test
    fun hud() {
        ShotHarness.render(
            rule, "hud_${theme.name}_${style.name}", theme, 900.dp, 400.dp, 1500,
            content = {
                HudScreen(
                    snapshot = VehicleSnapshot(
                        rpm = 5200f, previousRpm = 5200f, rpmSampleAgeMs = 50,
                        gpsSpeedMps = 28.2f, gpsHasFix = true, gear = 3,
                        coolantC = 92f, oilTempC = 104f, intakeAirTempC = 38f,
                        boostPsi = 14.2f, afr = 11.8f, oilPressureOk = true,
                        batteryVolts = 14.1f, throttlePercent = 72f,
                        obdConnected = true, usingSimulator = true
                    ),
                    preferences = HudPreferences(showSpeedLimit = true, tachVisualStyle = style),
                    connectionStatus = "Simulator",
                    motion = { MotionReading(0.2f, 0.4f, 0f, 0.45f, 0.2f, 0.4f, true) },
                    speedLimit = SpeedLimitReading(limit = 55),
                    onOpenSettings = {}
                )
            }
        )
    }
}
