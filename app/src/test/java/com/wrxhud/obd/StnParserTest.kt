package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StnParserTest {
    @Test
    fun decodeRpm_fromRecordedReply() {
        val data = StnParser.parseMode01("41 0C 1A F8", 0x0C)
        assertThat(data).isNotNull()
        // (0x1AF8)/4 = 1726
        assertThat(StnParser.decodeRpm(data!!)).isWithin(0.1f).of(1726f)
    }

    @Test
    fun decodeCoolant() {
        val data = StnParser.parseMode01("41057B", 0x05)!!
        assertThat(StnParser.decodeTempC(data)).isWithin(0.1f).of(83f)
    }

    @Test
    fun decodeMil_on() {
        val data = StnParser.parseMode01("41 01 83 00 00 00", 0x01)!!
        assertThat(StnParser.decodeMil(data)).isTrue()
    }

    @Test
    fun decodeMil_off() {
        val data = StnParser.parseMode01("410100000000", 0x01)!!
        assertThat(StnParser.decodeMil(data)).isFalse()
    }

    @Test
    fun decodeVoltage() {
        val data = StnParser.parseMode01("41 42 36 B0", 0x42)!!
        assertThat(StnParser.decodeVoltage(data)).isWithin(0.01f).of(14.0f)
    }

    @Test
    fun parseMode22() {
        val data = StnParser.parseMode22("62 11 53 6E", 0x1153)!!
        assertThat(data.size).isEqualTo(1)
        assertThat(data[0].toInt() and 0xFF).isEqualTo(0x6E)
    }

    @Test
    fun isError_noData() {
        assertThat(StnParser.isError("NO DATA")).isTrue()
        assertThat(StnParser.isError("41 0C 0F A0")).isFalse()
    }

    @Test
    fun decodeOdometerKm() {
        // 42150.0 km → raw = 421500 = 0x00066E7C
        val data = StnParser.parseMode01("41 A6 00 06 6E 7C", 0xA6)!!
        assertThat(StnParser.decodeOdometerKm(data)).isWithin(0.1f).of(42_150f)
    }

    @Test
    fun supportedPidBitmask() {
        // Bit 0 of first byte = PID 01 supported when MSB set in first nibble of response pattern
        val data = byteArrayOf(0xBE.toByte(), 0x1F, 0xA8.toByte(), 0x13)
        val supported = StnParser.decodeSupportedPidBitmask(data)
        assertThat(supported).contains(1)
        assertThat(supported).contains(0x0C)
    }
}
