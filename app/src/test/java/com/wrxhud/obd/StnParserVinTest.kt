package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StnParserVinTest {
    @Test
    fun decodeVinFromSingleFrameMode09() {
        // 49 02 01 + "JF1VA2E6XN" (partial) padded — use a full 17-char VIN in hex
        val vin = "JF1VA2E65N9000001"
        val asciiHex = vin.map { "%02X".format(it.code) }.joinToString("")
        val raw = "490201$asciiHex"
        assertThat(StnParser.decodeVinMode09(raw)).isEqualTo(vin)
    }

    @Test
    fun decodeVinFromMultiFrameMode09() {
        val vin = "JF1VA2E65N9000001"
        val bytes = vin.map { it.code.toByte() }
        val part1 = bytes.take(3).joinToString("") { "%02X".format(it) }
        val part2 = bytes.drop(3).joinToString("") { "%02X".format(it) }
        val raw = "49 02 01 $part1\r49 02 02 $part2"
        assertThat(StnParser.decodeVinMode09(raw)).isEqualTo(vin)
    }

    @Test
    fun decodeAsciiFieldStripsNul() {
        val data = byteArrayOf('A'.code.toByte(), 0, 'B'.code.toByte())
        assertThat(StnParser.decodeAsciiField(data)).isEqualTo("AB")
    }
}
