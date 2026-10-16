package com.wrxhud.hud

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.ShiftLightPattern
import org.junit.Test

class ShiftLightPatternTest {
    @Test
    fun leftToRight_order() {
        assertThat(ShiftLightPattern.LEFT_TO_RIGHT.physicalOrder(10))
            .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            .inOrder()
    }

    @Test
    fun rightToLeft_order() {
        assertThat(ShiftLightPattern.RIGHT_TO_LEFT.physicalOrder(10))
            .containsExactly(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
            .inOrder()
    }

    @Test
    fun outsideIn_order() {
        assertThat(ShiftLightPattern.OUTSIDE_IN.physicalOrder(10))
            .containsExactly(0, 9, 1, 8, 2, 7, 3, 6, 4, 5)
            .inOrder()
    }

    @Test
    fun insideOut_order() {
        assertThat(ShiftLightPattern.INSIDE_OUT.physicalOrder(10))
            .containsExactly(4, 5, 3, 6, 2, 7, 1, 8, 0, 9)
            .inOrder()
    }

    @Test
    fun resolve_outsideIn_lightsOuterPairFirst() {
        val thresholds = listOf(3300, 3600, 3900, 4200, 4500, 4800, 5100, 5400, 5700, 6100)
        val lamps = resolveShiftLamps(3350f, thresholds, ShiftLightPattern.OUTSIDE_IN)
        assertThat(lamps[0].lit).isTrue()
        assertThat(lamps[9].lit).isFalse() // second stage
        assertThat(lamps.count { it.lit }).isEqualTo(1)

        val two = resolveShiftLamps(3650f, thresholds, ShiftLightPattern.OUTSIDE_IN)
        assertThat(two[0].lit).isTrue()
        assertThat(two[9].lit).isTrue()
        assertThat(two.count { it.lit }).isEqualTo(2)
    }
}
