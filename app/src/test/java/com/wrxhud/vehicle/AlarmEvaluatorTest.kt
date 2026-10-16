package com.wrxhud.vehicle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlarmEvaluatorTest {
    private val thresholds = WrxProfile.AlarmThresholds()

    @Test
    fun flagsHighCoolant() {
        val snap = VehicleSnapshot(coolantC = 110f, milOn = false, gpsHasFix = true, obdConnected = true)
        val alarms = AlarmEvaluator.evaluate(snap, thresholds, expectObd = true)
        assertThat(alarms.highCoolant).isTrue()
        assertThat(alarms.cel).isFalse()
    }

    @Test
    fun flagsLinkLost() {
        val snap = VehicleSnapshot(obdConnected = false, usingSimulator = false, gpsHasFix = true)
        val alarms = AlarmEvaluator.evaluate(snap, thresholds, expectObd = true)
        assertThat(alarms.linkLost).isTrue()
    }

    @Test
    fun severityCritical() {
        assertThat(AlarmEvaluator.severityForCoolant(120f, thresholds))
            .isEqualTo(AlarmEvaluator.Severity.CRITICAL)
    }
}
