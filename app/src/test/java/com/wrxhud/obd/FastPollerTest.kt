package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.PollingMode
import org.junit.Test

class FastPollerTest {
    private val discovery = listOf("0100", "0120", "0140", "015C", "010F", "010B")
    private fun afterDiscovery(c: List<String>): List<String> {
        val i = c.indexOf("090A")
        return c.drop(i + 1)
    }

    private fun run(link: FakeEcuLink, assignments: List<PidAssignment> = emptyList()) =
        runPoller(link, PollingMode.FAST, assignments)

    @Test
    fun discoveryIsIdenticalToLegacy() {
        val fast = FakeEcuLink(closeAfter = 60).also { run(it) }
        val legacy = FakeEcuLink(closeAfter = 60).also { runPoller(it, PollingMode.LEGACY) }
        val n = legacy.commands.indexOf("090A") + 1
        assertThat(fast.commands.take(n)).isEqualTo(legacy.commands.take(n))
        assertThat(fast.commands.take(discovery.size)).isEqualTo(discovery)
    }

    @Test
    fun firstCycleIsOneBatchedRequestPlusSlowChunkThenHinted() {
        val link = FakeEcuLink(closeAfter = 80)
        run(link)
        val c = afterDiscovery(link.commands)
        // cycle 1: unhinted fast batch (rpm speed throttle pedal map mil), slow chunk 1
        assertThat(c[0]).isEqualTo("010C0D11490B01")
        assertThat(c[1]).startsWith("01").also { assertThat(c[1].length).isEqualTo(2 + 4 * 2) }
        // once learned single-ECU, the fast batch carries the count hint "1"
        assertThat(c.drop(2).first { it.startsWith("010C0D1149") }).endsWith("1")
        // requests per cycle is tiny compared to legacy (13 PIDs one by one)
        assertThat(c.count { it.startsWith("010C") }).isAtLeast(5)
    }

    @Test
    fun allValuesPopulateFromBatches() {
        val link = FakeEcuLink(closeAfter = 120)
        val poller = run(link)
        val s = poller.snapshot.value
        assertThat(s.rpm).isEqualTo(1726f)
        assertThat(s.obdSpeedKph).isEqualTo(60f)
        assertThat(s.throttlePercent).isNotNull()
        assertThat(s.acceleratorPercent).isNotNull()
        assertThat(s.coolantC).isEqualTo(83f)
        assertThat(s.intakeAirTempC).isEqualTo(18f)
        assertThat(s.oilTempC).isEqualTo(50f)
        assertThat(s.batteryVolts).isWithin(0.01f).of(13.8f)
        assertThat(s.fuelLevelPercent).isNotNull()
        assertThat(s.afr).isWithin(0.1f).of(14.7f)
        assertThat(s.boostPsi).isNotNull()
        assertThat(s.milOn).isFalse()
        assertThat(s.gear).isNotNull()
        assertThat(s.rpmSampleAgeMs).isAtLeast(0L)
    }

    @Test
    fun rpmKeepsPreviousRpmSemantics() {
        val link = FakeEcuLink(closeAfter = 100)
        var n = 0
        link.tamper = { cmd, r ->
            // vary rpm each fast batch
            if (cmd.startsWith("010C0D")) { n++; r.replace("1A F8", "%02X %02X".format(0x10 + n, 0x00)) } else r
        }
        val poller = run(link)
        val s = poller.snapshot.value
        assertThat(s.previousRpm).isNotNull()
        assertThat(s.previousRpm).isNotEqualTo(s.rpm)
        assertThat(s.timestampMs).isGreaterThan(0L)
    }

    @Test
    fun noRequestHasMoreThanSixPids() {
        val link = FakeEcuLink(closeAfter = 200)
        run(link)
        link.commands.filter { it.startsWith("01") && it.length > 4 }.forEach { cmd ->
            val body = cmd.substring(2).let { if (it.length % 2 == 1) it.dropLast(1) else it }
            assertThat(body.length / 2).isAtMost(6)
        }
    }

    @Test
    fun truncatedHintedReplyFallsBackToUnhintedAndRemembersPerPid() {
        val link = FakeEcuLink(closeAfter = 160)
        // With the hint the (pretend) adapter cuts the reply: only RPM comes back.
        link.tamper = { cmd, r ->
            if (cmd.startsWith("010C0D") && cmd.length % 2 == 1) "41 0C 1A F8" else r
        }
        run(link)
        val c = afterDiscovery(link.commands)
        val hinted = c.count { it.startsWith("010C0D") && it.length % 2 == 1 }
        // each fast PID combination is hinted once at most, then remembered as noHint
        assertThat(hinted).isAtMost(1)
        assertThat(c.count { it == "010C0D11490B" || it == "010C0D1149" || it == "010C0D11490B01" })
            .isAtLeast(3)
    }

    @Test
    fun secondEcuAnswerDisablesHintForThosePids() {
        val link = FakeEcuLink(closeAfter = 100, secondEcu = true)
        run(link)
        val c = afterDiscovery(link.commands)
        assertThat(c.none { it.startsWith("010C0D") && it.length % 2 == 1 }).isTrue()
    }

    @Test
    fun adapterRejectingMultiPidDegradesToSingles() {
        val link = FakeEcuLink(closeAfter = 220, rejectMultiPid = true)
        val poller = run(link)
        val c = afterDiscovery(link.commands)
        // after the first failed batch everything is requested one PID at a time
        val tail = c.takeLast(40)
        assertThat(tail.all { it.length <= 5 }).isTrue()
        assertThat(poller.snapshot.value.rpm).isEqualTo(1726f)
        assertThat(poller.snapshot.value.coolantC).isEqualTo(83f)
    }

    @Test
    fun oneFailingPidInBatchDegradesOnlyThatPid() {
        // speed (0D) is advertised but the batch reply never includes it; alone it works.
        val link = FakeEcuLink(closeAfter = 260)
        link.tamper = { cmd, r ->
            if (cmd.length > 5 && cmd.startsWith("01")) {
                r.replace("0D 3C", "").replace("0D3C", "")
            } else r
        }
        val poller = run(link)
        assertThat(poller.snapshot.value.obdSpeedKph).isEqualTo(60f)
        val c = afterDiscovery(link.commands)
        val tail = c.takeLast(30)
        assertThat(tail.any { it == "010D" || it == "010D1" }).isTrue()
        // RPM + throttle still batched together without speed
        assertThat(tail.any { it.startsWith("010C11") }).isTrue()
    }

    @Test
    fun mode22GroupedUnderOneHeaderSwitch() {
        val mode22 = mapOf(0x0113 to "6201138000", 0x0201 to "62020140", 0x0301 to "62030140")
        val link = FakeEcuLink(closeAfter = 120, mode22 = mode22)
        val assignments = listOf(
            PidAssignment(MappableSignal.AFR, 0x22, 0x0113, PidDecode.LAMBDA_AFR, 0x7E0),
            PidAssignment(MappableSignal.OIL_PRESSURE, 0x22, 0x0201, PidDecode.PRESSURE_KPA),
            PidAssignment(MappableSignal.OIL_TEMP, 0x22, 0x0301, PidDecode.TEMP_A40, 0x7E2)
        )
        val poller = run(link, assignments)
        val c = afterDiscovery(link.commands)
        val i = c.indexOf("ATSH7E0")
        assertThat(c.subList(i, i + 7))
            .containsExactly("ATSH7E0", "220113", "220201", "ATSH7E2", "220301", "ATSH7DF", c[i + 6])
            .inOrder()
        // header restored exactly once per group of switches, never 3 commands per DID
        assertThat(c.count { it == "ATSH7DF" }).isEqualTo(c.count { it == "ATSH7E0" })
        assertThat(poller.snapshot.value.afr).isWithin(0.1f).of(14.7f)
        assertThat(poller.snapshot.value.oilTempC).isEqualTo(24f)
    }

    @Test
    fun mode22MapRunsEverySecondCycleGrouped() {
        val link = FakeEcuLink(
            closeAfter = 160,
            supported = FakeEcuLink.DEFAULT_SUPPORTED - setOf(0x0B, 0x0F),
            mode22 = mapOf(0x0008 to "62000850", 0x000B to "62000B64")
        )
        val poller = run(link)
        val c = afterDiscovery(link.commands)
        // MAP DID polled behind ONE header switch, not 3 commands per poll
        assertThat(c.count { it == "22000B" }).isAtLeast(3)
        val k = c.indexOf("22000B")
        assertThat(c[k - 1]).isEqualTo("ATSH7E0")
        assertThat(poller.snapshot.value.boostPsi).isNotNull()
    }

    @Test
    fun modeSwitchesLiveBetweenLegacyAndFast() {
        val link = FakeEcuLink(closeAfter = 400)
        var mode = PollingMode.LEGACY
        var seen = 0
        link.tamper = { cmd, r ->
            seen++
            if (seen == 100) mode = PollingMode.FAST
            if (seen == 250) mode = PollingMode.LEGACY
            r
        }
        runPoller(link, PollingMode.LEGACY, modeProvider = { mode })
        val c = afterDiscovery(link.commands)
        // legacy part: only single-PID requests; fast part: a batched request appears; legacy again
        val firstBatch = c.indexOfFirst { it.length > 5 && it.startsWith("010C0D") }
        assertThat(firstBatch).isGreaterThan(40)
        val lastBatch = c.indexOfLast { it.length > 5 && it.startsWith("010C0D") }
        assertThat(lastBatch).isLessThan(c.size - 40)
        assertThat(c.take(firstBatch).all { it.length <= 4 || it.startsWith("ATSH") || it.startsWith("22") }).isTrue()
        assertThat(c.drop(lastBatch + 40).all { it.length <= 4 || it.startsWith("ATSH") || it.startsWith("22") }).isTrue()
    }

    @Test
    fun pollStatsArePublished() {
        val link = FakeEcuLink(closeAfter = 60)
        val poller = run(link)
        // window is 1 s; a fast fake finishes sooner, so only check it is consistent
        assertThat(poller.pollStats.value.rpmPerSec).isAtLeast(0f)
    }
}
