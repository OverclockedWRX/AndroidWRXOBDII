package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PidAssignmentTest {
    @Test
    fun encodeDecode_roundTrip() {
        val a = PidAssignment(
            signal = MappableSignal.OIL_TEMP,
            mode = 0x22,
            id = 0x0113,
            decode = PidDecode.TEMP_A40,
            header = 0x7E0
        )
        val restored = PidAssignment.decode(a.encode())
        assertThat(restored).isEqualTo(a)
    }

    @Test
    fun encodeDecode_mode01() {
        val a = PidAssignment(
            signal = MappableSignal.AFR,
            mode = 0x01,
            id = 0x34,
            decode = PidDecode.LAMBDA_AFR
        )
        val restored = PidAssignment.decode(a.encode())!!
        assertThat(restored.signal).isEqualTo(MappableSignal.AFR)
        assertThat(restored.mode).isEqualTo(0x01)
        assertThat(restored.id).isEqualTo(0x34)
        assertThat(restored.header).isNull()
    }

    @Test
    fun decodeTempAndPressure() {
        val temp = PidValueDecoder.decode(byteArrayOf(0x7A.toByte()), PidDecode.TEMP_A40)
        assertThat(temp).isWithin(0.1f).of(82f)

        val kpa = PidValueDecoder.decode(byteArrayOf(0x64), PidDecode.PRESSURE_KPA)
        assertThat(kpa).isWithin(0.1f).of(100f)

        val psi = PidValueDecoder.toSnapshotValue(
            MappableSignal.OIL_PRESSURE,
            PidDecode.PRESSURE_KPA,
            100f
        )
        assertThat(psi).isWithin(0.2f).of(14.5f)
    }
}
