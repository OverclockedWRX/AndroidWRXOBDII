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
        assertThat(ShiftLightPattern.LEFT_TO_RIGHT.lampCount()).isEqualTo(10)
    }

    @Test
    fun rightToLeft_order() {
        assertThat(ShiftLightPattern.RIGHT_TO_LEFT.physicalOrder(10))
            .containsExactly(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
            .inOrder()
    }

    @Test
    fun outsideIn_usesElevenLampsAndPairedGroups() {
        assertThat(ShiftLightPattern.OUTSIDE_IN.lampCount()).isEqualTo(11)
        assertThat(ShiftLightPattern.OUTSIDE_IN.lightingGroups()).containsExactly(
            listOf(0, 10),
            listOf(1, 9),
            listOf(2, 8),
            listOf(3, 7),
            listOf(4, 6),
            listOf(5)
        ).inOrder()
    }

    @Test
    fun insideOut_usesElevenLampsAndPairedGroups() {
        assertThat(ShiftLightPattern.INSIDE_OUT.lampCount()).isEqualTo(11)
        assertThat(ShiftLightPattern.INSIDE_OUT.lightingGroups()).containsExactly(
            listOf(5),
            listOf(4, 6),
            listOf(3, 7),
            listOf(2, 8),
            listOf(1, 9),
            listOf(0, 10)
        ).inOrder()
    }

    @Test
    fun resolve_outsideIn_lightsOuterPairTogether() {
        val thresholds = listOf(3300, 3600, 3900, 4200, 4500, 4800, 5100, 5400, 5700, 6100)
        val lamps = resolveShiftLamps(3350f, thresholds, ShiftLightPattern.OUTSIDE_IN)
        assertThat(lamps).hasSize(11)
        assertThat(lamps[0].lit).isTrue()
        assertThat(lamps[10].lit).isTrue()
        assertThat(lamps.count { it.lit }).isEqualTo(2)
    }

    @Test
    fun resolve_insideOut_lightsCenterFirst() {
        val thresholds = listOf(3300, 3600, 3900, 4200, 4500, 4800, 5100, 5400, 5700, 6100)
        val lamps = resolveShiftLamps(3350f, thresholds, ShiftLightPattern.INSIDE_OUT)
        assertThat(lamps).hasSize(11)
        assertThat(lamps[5].lit).isTrue()
        assertThat(lamps.count { it.lit }).isEqualTo(1)
    }
}
