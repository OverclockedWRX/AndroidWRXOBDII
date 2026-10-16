package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import com.wrxhud.vehicle.WrxProfile
import org.junit.Test

class AfrDecoderTest {
    @Test
    fun decodeAfr_stoich() {
        // λ = 1.0 → raw = 32768 = 0x8000
        val data = StnParser.parseMode01("41 34 80 00 00 00", 0x34)!!
        assertThat(StnParser.decodeEquivalenceRatio(data)!!).isWithin(0.01f).of(1.0f)
        assertThat(StnParser.decodeAfr(data, WrxProfile.STOICH_AFR)!!)
            .isWithin(0.05f).of(14.7f)
    }

    @Test
    fun decodeAfr_rich() {
        // λ ≈ 0.85 → AFR ≈ 12.5
        val raw = (0.85f * 32768f).toInt()
        val a = (raw shr 8) and 0xFF
        val b = raw and 0xFF
        val hex = "41 34 %02X %02X 00 00".format(a, b)
        val data = StnParser.parseMode01(hex, 0x34)!!
        assertThat(StnParser.decodeAfr(data)!!).isWithin(0.2f).of(12.5f)
    }
}
