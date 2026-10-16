package com.wrxhud.vehicle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GearCalculatorTest {
    private val circ = WrxProfile.tireCircumferenceMeters()

    @Test
    fun estimatesThirdGear() {
        // overall = ratio * final = 1.367 * 4.111 ≈ 5.62
        // wheel rpm = rpm / overall; speed = wheelrpm * circ / 60
        val rpm = 3500.0
        val overall = 1.367 * 4.111
        val wheelRpm = rpm / overall
        val speedMps = wheelRpm * circ / 60.0

        val gear = GearCalculator.estimateGear(
            rpm = rpm,
            speedMps = speedMps,
            transmission = WrxProfile.manual6,
            tireCircumferenceM = circ
        )
        assertThat(gear).isEqualTo(3)
    }

    @Test
    fun returnsNullWhenStopped() {
        val gear = GearCalculator.estimateGear(
            rpm = 900.0,
            speedMps = 0.2,
            transmission = WrxProfile.manual6,
            tireCircumferenceM = circ
        )
        assertThat(gear).isNull()
    }

    @Test
    fun estimatesSixthGear() {
        val rpm = 3000.0
        val overall = 0.667 * 4.111
        val wheelRpm = rpm / overall
        val speedMps = wheelRpm * circ / 60.0

        val gear = GearCalculator.estimateGear(
            rpm = rpm,
            speedMps = speedMps,
            transmission = WrxProfile.manual6,
            tireCircumferenceM = circ
        )
        assertThat(gear).isEqualTo(6)
    }
}
