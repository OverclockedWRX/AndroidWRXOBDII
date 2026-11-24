package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ObdCommandsRangeTest {
    @Test
    fun countHint_bounds() {
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.mode01Multi(listOf(0x0C), 0) }
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.mode01Multi(listOf(0x0C), 16) }
        assertThat(ObdCommands.mode01Multi(listOf(0x0C), 1)).isEqualTo("010C1")
        assertThat(ObdCommands.mode01Multi(listOf(0x0C), 15)).isEqualTo("010CF")
        assertThat(ObdCommands.mode01Multi(listOf(0x0C, 0x0D))).isEqualTo("010C0D")
    }

    @Test
    fun mode22_and_header_ranges() {
        assertThat(ObdCommands.mode22(0xFFFF)).isEqualTo("22FFFF")
        assertThat(ObdCommands.setHeader(0xFFF)).isEqualTo("ATSHFFF")
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.mode22(0x10000) }
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.mode22(-1) }
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.setHeader(0x1000) }
        assertThrows(IllegalArgumentException::class.java) { ObdCommands.setHeader(-1) }
    }

    @Test
    fun decode_dropsOutOfRangeValues() {
        assertThat(PidAssignment.decode("AFR|22|10000|LAMBDA_AFR")).isNull()
        assertThat(PidAssignment.decode("AFR|22|113|LAMBDA_AFR|1000")).isNull()
        assertThat(PidAssignment.decode("AFR|22|113|LAMBDA_AFR|zz")).isNull()
        assertThat(PidAssignment.decode("AFR|01|100|LAMBDA_AFR")).isNull()
        assertThat(PidAssignment.decode("AFR|05|1|LAMBDA_AFR")).isNull()
        assertThat(PidAssignment.decode("AFR|22|FFFF|LAMBDA_AFR|FFF")).isNotNull()
        assertThat(PidAssignment.decode("AFR|22|113|LAMBDA_AFR|7E0")).isNotNull()
    }
}
