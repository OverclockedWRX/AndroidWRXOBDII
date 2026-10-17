package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DtcParserTest {
    @Test
    fun formatDtc_powertrain() {
        // P0301 = 0x03 0x01
        assertThat(DtcParser.formatDtc(0x03, 0x01)).isEqualTo("P0301")
    }

    @Test
    fun formatDtc_network() {
        // U0100
        assertThat(DtcParser.formatDtc(0xC1, 0x00)).isEqualTo("U0100")
    }

    @Test
    fun parseStoredMode03() {
        val codes = DtcParser.parseModeResponse("43 03 01 01 71", 0x43, DtcBank.STORED)
        assertThat(codes.map { it.code }).containsExactly("P0301", "P0171").inOrder()
        assertThat(codes.all { it.bank == DtcBank.STORED }).isTrue()
    }

    @Test
    fun parseSkipsZeroPadding() {
        val codes = DtcParser.parseModeResponse("43 00 00", 0x43, DtcBank.STORED)
        assertThat(codes).isEmpty()
    }

    @Test
    fun milAndCount() {
        val (mil, count) = DtcParser.milAndCountFromPid01(byteArrayOf(0x83.toByte()))!!
        assertThat(mil).isTrue()
        assertThat(count).isEqualTo(3)
    }
}
