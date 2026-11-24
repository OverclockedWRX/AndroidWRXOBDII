package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StnParserMultiPidTest {
    private fun hex(b: ByteArray?) = b?.joinToString("") { "%02X".format(it) }

    @Test
    fun singlePidSingleFrame() {
        val r = StnParser.parseMultiPid("41 0C 1A F8 \r\r>", listOf(0x0C))
        assertThat(hex(r.values[0x0C])).isEqualTo("1AF8")
        assertThat(r.missing).isEmpty()
        assertThat(r.responseCount).isEqualTo(1)
    }

    @Test
    fun singlePidWithHintResponse() {
        val r = StnParser.parseMultiPid("410C1AF8\r", listOf(0x0C))
        assertThat(hex(r.values[0x0C])).isEqualTo("1AF8")
    }

    @Test
    fun threePidsInOneSingleFrame() {
        // 41 + 0C(2) + 0D(1) + 11(1) = 7 bytes -> fits one CAN frame.
        val r = StnParser.parseMultiPid("41 0C 1A F8 0D 3C 11 40\r\r", listOf(0x0C, 0x0D, 0x11))
        assertThat(hex(r.values[0x0C])).isEqualTo("1AF8")
        assertThat(hex(r.values[0x0D])).isEqualTo("3C")
        assertThat(hex(r.values[0x11])).isEqualTo("40")
        assertThat(r.missing).isEmpty()
    }

    @Test
    fun fourPidsMultiFrameIsoTp() {
        val raw = "00A\r0: 41 0C 1A F8 0D 3C\r1: 11 40 49 30\r\r>"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D, 0x11, 0x49))
        assertThat(hex(r.values[0x0C])).isEqualTo("1AF8")
        assertThat(hex(r.values[0x0D])).isEqualTo("3C")
        assertThat(hex(r.values[0x11])).isEqualTo("40")
        assertThat(hex(r.values[0x49])).isEqualTo("30")
        assertThat(r.missing).isEmpty()
        assertThat(r.responseCount).isEqualTo(1)
    }

    @Test
    fun multiFrameWithPaddingTrimmedByLengthLine() {
        // 8 payload bytes + 5 padding bytes (0x55) in the last frame.
        val raw = "008\r0: 41 0C 1A F8 0D 3C\r1: 11 40 55 55 55 55 55\r"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D, 0x11))
        assertThat(r.missing).isEmpty()
        assertThat(hex(r.values[0x11])).isEqualTo("40")
    }

    @Test
    fun multiFrameWithoutLengthLine() {
        val raw = "0: 41 0C 1A F8 0D 3C\r1: 11 40 49 30\r"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D, 0x11, 0x49))
        assertThat(r.missing).isEmpty()
        assertThat(r.responseCount).isEqualTo(1)
    }

    @Test
    fun noDataIsAllMissing() {
        val r = StnParser.parseMultiPid("NO DATA\r\r>", listOf(0x0C, 0x0D))
        assertThat(r.values).isEmpty()
        assertThat(r.missing).containsExactly(0x0C, 0x0D).inOrder()
        assertThat(r.responseCount).isEqualTo(0)
    }

    @Test
    fun emptyAndGarbageAreMissing() {
        assertThat(StnParser.parseMultiPid("", listOf(0x0C)).missing).containsExactly(0x0C)
        assertThat(StnParser.parseMultiPid("?", listOf(0x0C)).missing).containsExactly(0x0C)
        assertThat(StnParser.parseMultiPid("CAN ERROR", listOf(0x0C)).missing).containsExactly(0x0C)
        assertThat(StnParser.parseMultiPid("7F 01 12", listOf(0x0C)).missing).containsExactly(0x0C)
    }

    @Test
    fun searchingPrefixIsIgnored() {
        val r = StnParser.parseMultiPid("SEARCHING...\r41 0C 0B B8\r\r>", listOf(0x0C))
        assertThat(hex(r.values[0x0C])).isEqualTo("0BB8")
    }

    @Test
    fun partialMultiFrameKeepsOnlyCompletePids() {
        // Declared 10 bytes, second frame cut off after 0x11 -> 0x49 missing.
        val raw = "00A\r0: 41 0C 1A F8 0D 3C\r1: 11\r"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D, 0x11, 0x49))
        assertThat(r.values.keys).containsExactly(0x0C, 0x0D)
        assertThat(r.missing).containsExactly(0x11, 0x49).inOrder()
    }

    @Test
    fun truncatedInsideAPidDropsThatPid() {
        // 0C needs 2 data bytes but only 1 arrived.
        val r = StnParser.parseMultiPid("41 0D 3C 0C 1A", listOf(0x0D, 0x0C))
        assertThat(hex(r.values[0x0D])).isEqualTo("3C")
        assertThat(r.missing).containsExactly(0x0C)
    }

    @Test
    fun secondEcuLineCountsButFirstWins() {
        val raw = "41 0C 1A F8\r41 0C 00 00\r\r>"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C))
        assertThat(hex(r.values[0x0C])).isEqualTo("1AF8")
        assertThat(r.responseCount).isEqualTo(2)
    }

    @Test
    fun secondEcuAnswersOnlyMissingPid() {
        // ECM answers 0C, TCM answers 0D in its own line.
        val raw = "41 0C 1A F8\r41 0D 3C\r"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D))
        assertThat(r.missing).isEmpty()
        assertThat(r.responseCount).isEqualTo(2)
    }

    @Test
    fun unknownLengthPidAloneTakesRestOfFrame() {
        val r = StnParser.parseMultiPid("41 87 01 12 34", listOf(0x87))
        assertThat(hex(r.values[0x87])).isEqualTo("011234")
    }

    @Test
    fun unknownLengthPidInBatchStopsParsing() {
        val r = StnParser.parseMultiPid("41 0C 1A F8 87 01 12 34", listOf(0x0C, 0x87))
        assertThat(r.values.keys).containsExactly(0x0C)
        assertThat(r.missing).containsExactly(0x87)
    }

    @Test
    fun strayPidsNotRequestedAreIgnored() {
        val r = StnParser.parseMultiPid("41 0C 1A F8 0D 3C", listOf(0x0C))
        assertThat(r.values.keys).containsExactly(0x0C)
    }

    @Test
    fun wholeRealisticSixPidBatch() {
        // 0C 0D 11 49 0B 01(MIL, 4 bytes): 1 + 3+2+2+2+2+5 = 17 bytes -> 3 frames.
        val raw = "011\r0: 41 0C 1A F8 0D 3C\r1: 11 40 49 30 0B 37\r2: 01 80 07 61 00\r"
        val r = StnParser.parseMultiPid(raw, listOf(0x0C, 0x0D, 0x11, 0x49, 0x0B, 0x01))
        assertThat(r.missing).isEmpty()
        assertThat(StnParser.decodeMil(r.values[0x01]!!)).isTrue()
        assertThat(StnParser.decodeRpm(r.values[0x0C]!!)).isEqualTo(1726f)
    }
}
