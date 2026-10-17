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
}
