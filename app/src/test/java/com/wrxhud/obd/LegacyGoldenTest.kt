package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.PollingMode
import org.junit.Test

class LegacyGoldenTest {
    companion object {
        val SCENARIO_B_SUPPORTED = FakeEcuLink.DEFAULT_SUPPORTED - setOf(0x0B, 0x0F)
        val SCENARIO_B_MODE22 = mapOf(
            0x0008 to "62000850",
            0x000B to "62000B64",
            0x0113 to "6201138000",
            0x0201 to "62020140"
        )
        val SCENARIO_B_ASSIGNMENTS = listOf(
            PidAssignment(MappableSignal.COOLANT, 0x01, 0x05, PidDecode.TEMP_A40),
            PidAssignment(MappableSignal.AFR, 0x22, 0x0113, PidDecode.LAMBDA_AFR, 0x7E2),
            PidAssignment(MappableSignal.OIL_PRESSURE, 0x22, 0x0201, PidDecode.PRESSURE_KPA)
        )
    }

    private fun golden(name: String): List<String> =
        javaClass.getResource("/golden/$name")!!.readText().trim().split("|")

    /** Phase-2 (f4c158a) stream, all MX+ defaults: Mode 01 MAP/IAT, no assignments. */
    @Test
    fun legacyMatchesPhase2GoldenScenarioA() {
        val link = FakeEcuLink(closeAfter = 120)
        runPoller(link, PollingMode.LEGACY)
        assertThat(link.commands).isEqualTo(golden("legacy-a.txt"))
    }

    /** Phase-2 stream with Mode 22 IAT/MAP discovery and user assignments (custom header 7E2). */
    @Test
    fun legacyMatchesPhase2GoldenScenarioB() {
        val link = FakeEcuLink(closeAfter = 150, supported = SCENARIO_B_SUPPORTED, mode22 = SCENARIO_B_MODE22)
        runPoller(link, PollingMode.LEGACY, SCENARIO_B_ASSIGNMENTS)
        assertThat(link.commands).isEqualTo(golden("legacy-b.txt"))
    }
}
