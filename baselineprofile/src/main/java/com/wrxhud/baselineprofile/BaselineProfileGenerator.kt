package com.wrxhud.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile for cold start and the first HUD frames.
 *
 * Run with `sh ./gradlew :app:generateBaselineProfile` on a machine with an emulator or device;
 * the result lands in app/src/main/generated/baselineProfiles and is merged with the
 * hand-written app/src/main/baseline-prof.txt. NOT run on the build host (no KVM).
 *
 * Preconditions on the target: Bluetooth/location permissions granted (adb install -g) and
 * onboarding completed with "Use simulator" on, so the HUD streams simulated telemetry.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndHud() = rule.collect(packageName = "com.wrxhud") {
        pressHome()
        startActivityAndWait()
        // Let the simulator drive a few seconds of RPM / shift-light / gear animation.
        device.waitForIdle(5_000)
        Thread.sleep(5_000)
    }
}
