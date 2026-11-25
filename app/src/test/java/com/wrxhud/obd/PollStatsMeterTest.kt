package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import com.wrxhud.settings.PollingMode
import org.junit.Test

class PollStatsMeterTest {
    @Test
    fun reportsRatesPerSecondAndResets() {
        val m = PollStatsMeter()
        assertThat(m.tick(0, PollingMode.FAST)).isNull()
        repeat(40) { m.rpmSample(); m.cycle() }
        repeat(60) { m.request(10_000_000L) }
        m.request(50_000_000L)
        assertThat(m.tick(500, PollingMode.FAST)).isNull()
        val s = m.tick(2000, PollingMode.FAST)!!
        assertThat(s.mode).isEqualTo(PollingMode.FAST)
        assertThat(s.rpmPerSec).isWithin(0.01f).of(20f)
        assertThat(s.cyclesPerSec).isWithin(0.01f).of(20f)
        assertThat(s.requestsPerSec).isWithin(0.01f).of(30.5f)
        assertThat(s.maxLatencyMs).isWithin(0.01f).of(50f)
        assertThat(s.avgLatencyMs).isWithin(0.1f).of(10.65f)
        assertThat(m.tick(3000, PollingMode.FAST)!!.rpmPerSec).isEqualTo(0f)
    }

    @Test
    fun logLineEveryTenSeconds() {
        val m = PollStatsMeter()
        m.tick(0, PollingMode.LEGACY)
        repeat(30) { m.rpmSample(); m.cycle(); m.request(20_000_000L) }
        assertThat(m.logLine(9_000, PollingMode.LEGACY)).isNull()
        val line = m.logLine(10_000, PollingMode.LEGACY)!!
        assertThat(line).contains("mode=LEGACY")
        assertThat(line).contains("rpm=3.0/s")
        assertThat(line).contains("avg=20ms")
        assertThat(m.logLine(10_500, PollingMode.LEGACY)).isNull()
    }
}
