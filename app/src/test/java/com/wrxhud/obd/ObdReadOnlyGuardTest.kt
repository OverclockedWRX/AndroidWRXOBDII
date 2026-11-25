package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ObdReadOnlyGuardTest {
    @Test
    fun allowsMode01AndMode22Reads() {
        assertThat(ObdReadOnlyGuard.isAllowed("010C")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("015C")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("220113")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("22F190")).isTrue()
    }

    @Test
    fun allowsMode09VehicleInfoReads() {
        assertThat(ObdReadOnlyGuard.isAllowed("0902")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("0904")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("090A")).isTrue()
    }

    @Test
    fun allowsDtcReadAndClear() {
        assertThat(ObdReadOnlyGuard.isAllowed("03")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("07")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("0A")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("04")).isTrue()
    }

    @Test
    fun allowsAdapterInit() {
        assertThat(ObdReadOnlyGuard.isAllowed("ATZ")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("ATE0")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("ATAL")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("ATSP6")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("ATSH7E0")).isTrue()
        assertThat(ObdReadOnlyGuard.isAllowed("STPTO32")).isTrue()
    }

    @Test
    fun blocksProgrammingAndIoServices() {
        // Session / write DID / IO / routine / download / memory — never allowed
        listOf(
            "08",
            "10",
            "11",
            "14",
            "2E1234AABB",
            "2F1234",
            "310102",
            "34",
            "35",
            "36",
            "37",
            "3D1234",
            "3E00",
            "23",
            "AAAA"
        ).forEach { cmd ->
            assertThat(ObdReadOnlyGuard.isAllowed(cmd)).isFalse()
            assertThrows(SecurityException::class.java) {
                ObdReadOnlyGuard.assertAllowed(cmd)
            }
        }
    }

    @Test
    fun allowsReadOnlyMultiPidAndResponseCountForms() {
        listOf(
            "010C1", "010D1", "010C0D", "010C0D11", "010C0D111", "010C0D1149",
            "010C0D11490B33", "010C0D11490B331", "01 0C 0D 11"
        ).forEach { cmd ->
            assertThat(ObdReadOnlyGuard.isAllowed(cmd.uppercase().replace(" ", ""))).isTrue()
        }
        assertThat(ObdCommands.mode01Multi(listOf(0x0C, 0x0D, 0x11), 1)).isEqualTo("010C0D111")
        assertThat(ObdCommands.mode01Multi(listOf(0x0C))).isEqualTo("010C")
        assertThat(ObdCommands.mode01Multi(listOf(0x0C), 1)).isEqualTo("010C1")
        ObdReadOnlyGuard.assertAllowed(ObdCommands.mode01Multi(listOf(1, 2, 3, 4, 5, 6), 1))
    }

    @Test
    fun stillBlocksWritesAndOverlongOrOddMode01Forms() {
        listOf(
            "010C0D11490B330C1", // 7 PIDs
            "010C0",             // count digit 0 is not a valid hint
            "01",                // no PID
            "010",
            "0C1",               // not mode 01
            "2E010C1",           // write DID
            "0101ZZ",
            "010C1;ATZ",
            "041",               // clear DTCs must stay exactly 04
            "221234AA"
        ).forEach { cmd ->
            assertThat(ObdReadOnlyGuard.isAllowed(cmd)).isFalse()
        }
    }
}
