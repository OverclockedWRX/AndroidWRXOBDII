package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.PollingMode
import org.junit.Test

/** An exception mid Mode 22 group must never leave the adapter header at 7E0 / custom. */
class HeaderRestoreTest {
    private val mode22 = mapOf(0x0113 to "6201138000", 0x0201 to "62020140")
    private val assignments = listOf(
        PidAssignment(MappableSignal.AFR, 0x22, 0x0113, PidDecode.LAMBDA_AFR, 0x7E0),
        PidAssignment(MappableSignal.OIL_PRESSURE, 0x22, 0x0201, PidDecode.PRESSURE_KPA, 0x7E2)
    )

    private fun throwingLink(on: String) = FakeEcuLink(closeAfter = 400, mode22 = mode22).also { link ->
        link.tamper = { cmd, r -> if (cmd == on) throw IllegalStateException("link broke") else r }
    }

    @Test
    fun fast_exceptionMidGroupRestoresHeader() {
        val link = throwingLink("220201")
        runPoller(link, PollingMode.FAST, assignments)
        assertThat(link.commands.last()).isEqualTo("ATSH7DF")
        assertThat(link.commands).contains("220201")
        assertThat(link.commands.takeLast(2)).containsExactly("220201", "ATSH7DF").inOrder()
    }

    @Test
    fun legacy_exceptionMidMode22RestoresHeader() {
        val link = throwingLink("220113")
        runPoller(link, PollingMode.LEGACY, assignments)
        assertThat(link.commands.takeLast(2)).containsExactly("220113", "ATSH7DF").inOrder()
    }

    @Test
    fun restoreFailureDoesNotMaskOriginalAndPollerEnds() {
        val link = FakeEcuLink(closeAfter = 400, mode22 = mode22)
        link.tamper = { cmd, r ->
            if (cmd == "220201" || (cmd == "ATSH7DF" && link.commands.contains("220201"))) {
                throw IllegalStateException("dead")
            } else r
        }
        runPoller(link, PollingMode.FAST, assignments) // must return, not hang/throw
        assertThat(link.commands.last()).isEqualTo("ATSH7DF")
    }

    @Test
    fun badStoredAssignmentDoesNotThrowAndIsIgnored() {
        val bad = listOf(
            PidAssignment(MappableSignal.AFR, 0x22, 0x1FFFF, PidDecode.LAMBDA_AFR, 0x7E0),
            PidAssignment(MappableSignal.OIL_TEMP, 0x22, 0x0301, PidDecode.TEMP_A40, 0x1FFF)
        )
        for (mode in PollingMode.values()) {
            val link = FakeEcuLink(closeAfter = 150, mode22 = mode22)
            runPoller(link, mode, bad)
            assertThat(link.commands.size).isAtLeast(100) // ran normally to the scripted close
            assertThat(link.commands.filter { it.startsWith("ATSH") && it != "ATSH7DF" && it != "ATSH7E0" })
                .isEmpty()
        }
    }
}
